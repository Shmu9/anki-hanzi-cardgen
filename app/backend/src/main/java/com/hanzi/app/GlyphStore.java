package com.hanzi.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanzi.app.utils.IdsLabels;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GlyphStore {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

	private final Path dbPath;

	public GlyphStore(Path dbPath) {
		this.dbPath = dbPath;
	}

	public Map<String, Object> metadata() throws SQLException {
		Map<String, String> metadata = new LinkedHashMap<>();
		int glyphCount;
		int unicodeCount;
		try (Connection conn = connect()) {
			try (PreparedStatement statement = conn.prepareStatement("SELECT key, value FROM metadata ORDER BY key");
					ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					metadata.put(rows.getString("key"), rows.getString("value"));
				}
			}
			glyphCount = count(conn, "SELECT COUNT(*) AS count FROM glyphs");
			unicodeCount = count(conn, "SELECT COUNT(*) AS count FROM glyphs WHERE kind = 'unicode'");
		}
		return mapOf(
				"db_path", dbPath.toString(),
				"glyph_count", glyphCount,
				"unicode_count", unicodeCount,
				"metadata", metadata);
	}

	public List<Map<String, Object>> search(String query, String hsk, String strokeMin, String strokeMax, int limit)
			throws SQLException {
		List<String> where = new ArrayList<>();
		List<Object> whereParams = new ArrayList<>();
		List<String> rankParts = new ArrayList<>();
		List<Object> orderParams = new ArrayList<>();
		where.add("kind = 'unicode'");

		if (query != null && !query.isBlank()) {
			String normalized = query.trim();
			String upper = normalized.toUpperCase();
			String like = "%" + normalized + "%";
			where.add("""
					(
						glyph = ?
						OR token = ?
						OR unicode = ?
						OR k_mandarin LIKE ?
						OR k_definition LIKE ?
						OR k_japanese LIKE ?
						OR k_hanyu_pinlu LIKE ?
					)
					""");
			whereParams.addAll(List.of(normalized, upper, upper, like, like, like, like));
			rankParts.add("""
					CASE
						WHEN glyph = ? THEN 0
						WHEN token = ? OR unicode = ? THEN 1
						WHEN k_mandarin = ? THEN 2
						WHEN k_mandarin LIKE ? THEN 3
						WHEN k_definition LIKE ? THEN 4
						ELSE 5
					END
					""");
			orderParams.addAll(List.of(normalized, upper, upper, normalized, normalized + "%", like));
		}

		if (hsk != null && !hsk.isBlank() && !"any".equals(hsk)) {
			where.add("hsk_level = ?");
			whereParams.add(Integer.parseInt(hsk));
		}
		if (strokeMin != null && !strokeMin.isBlank()) {
			where.add("stroke_count >= ?");
			whereParams.add(Integer.parseInt(strokeMin));
		}
		if (strokeMax != null && !strokeMax.isBlank()) {
			where.add("stroke_count <= ?");
			whereParams.add(Integer.parseInt(strokeMax));
		}

		List<String> order = new ArrayList<>(rankParts);
		order.add("CASE WHEN frequency_rank IS NULL THEN 1 ELSE 0 END");
		order.add("frequency_rank");
		order.add("CASE WHEN stroke_count IS NULL THEN 1 ELSE 0 END");
		order.add("stroke_count");

		String sql = """
				SELECT
					id, token, glyph, unicode, codepoint, kind, k_definition, k_mandarin,
					k_japanese, k_hanyu_pinlu, frequency_rank, hsk_level, stroke_count,
					rs_unicode, rs_adobe_japan1_6, decomp_type, decomp_components
				FROM glyphs
				WHERE %s
				ORDER BY %s
				LIMIT ?
				""".formatted(String.join(" AND ", where), String.join(", ", order));

		List<Object> params = new ArrayList<>(whereParams);
		params.addAll(orderParams);
		params.add(Math.max(1, Math.min(limit, 120)));
		return queryRows(sql, params);
	}

	public Map<String, Object> entry(String key) throws SQLException {
		Map<String, Object> root = findGlyph(key);
		if (root == null) {
			return null;
		}

		Map<String, Object> tree;
		try (Connection conn = connect()) {
			tree = treeForRow(conn, root, 0, Set.of());
		}

		Object glyph = root.get("glyph");
		List<Map<String, Object>> referencedBy = glyph == null ? List.of() : componentRefs(glyph.toString());
		return mapOf(
				"entry", root,
				"decomposition_tree", tree,
				"base_components_with_definition", baseComponentsWithDefinition(root),
				"base_components", baseComponentsFromTree(tree),
				"referenced_by", referencedBy);
	}

	private Connection connect() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:file:" + dbPath.toAbsolutePath() + "?mode=ro");
	}

	private int count(Connection conn, String sql) throws SQLException {
		try (PreparedStatement statement = conn.prepareStatement(sql);
				ResultSet rows = statement.executeQuery()) {
			return rows.next() ? rows.getInt("count") : 0;
		}
	}

	private Map<String, Object> findGlyph(String key) throws SQLException {
		String normalized = key == null ? "" : key.trim();
		String sql = """
				SELECT *
				FROM glyphs
				WHERE glyph = ?
					  OR token = ?
					  OR unicode = ?
				LIMIT 1
				""";
		List<Map<String, Object>> rows = queryRows(sql, List.of(normalized, normalized.toUpperCase(), normalized.toUpperCase()));
		return rows.isEmpty() ? null : rows.get(0);
	}

	private List<Map<String, Object>> componentRefs(String glyph) throws SQLException {
		String sql = """
				SELECT glyph, token, k_definition, k_mandarin, stroke_count, frequency_rank
				FROM glyphs
				WHERE kind = 'unicode'
					AND decomp_components LIKE ?
				ORDER BY
					CASE WHEN frequency_rank IS NULL THEN 1 ELSE 0 END,
					frequency_rank,
					CASE WHEN stroke_count IS NULL THEN 1 ELSE 0 END,
					stroke_count,
					glyph
				LIMIT 80
				""";
		return queryRows(sql, List.of("%\"" + glyph + "\"%"));
	}

	private List<Map<String, Object>> baseComponentsWithDefinition(Map<String, Object> root) throws SQLException {
		String sql = """
				SELECT
					summary.component_glyph AS glyph,
					summary.component_token AS token,
					summary.position,
					summary.depth,
					glyphs.k_definition AS definition,
					glyphs.k_mandarin AS mandarin
				FROM glyph_base_components_with_definition AS summary
				JOIN glyphs
					ON glyphs.id = summary.component_glyph_id
				WHERE summary.root_glyph = ?
					  OR summary.root_token = ?
				ORDER BY summary.depth, summary.position, summary.component_token
				""";
		List<Object> params = new ArrayList<>();
		params.add(root.get("glyph"));
		params.add(root.get("token"));
		return dedupeComponents(queryRows(sql, params));
	}

	private Map<String, Object> lookupComponent(Connection conn, String key) throws SQLException {
		String sql = """
				SELECT *
				FROM glyphs
				WHERE glyph = ?
					  OR token = ?
					  OR unicode = ?
				LIMIT 1
				""";
		try (PreparedStatement statement = conn.prepareStatement(sql)) {
			bind(statement, List.of(key, key.toUpperCase(), key.toUpperCase()));
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rowToMap(rows) : null;
			}
		}
	}

	private Map<String, Object> treeForRow(Connection conn, Map<String, Object> row, int depth, Set<String> seen)
			throws SQLException {
		String token = stringValue(row.get("token"));
		if (token == null) {
			token = stringValue(row.get("glyph"));
		}
		if (token == null) {
			token = "";
		}
		List<String> components = parseComponents(stringValue(row.get("decomp_components")));
		String kind = stringValue(row.get("kind"));
		String glyph = stringValue(row.get("glyph"));
		boolean intermediate = "intermediate".equals(kind)
				|| ("literal".equals(kind) && IdsLabels.isIdsExpression(glyph != null ? glyph : token));

		Map<String, Object> node = mapOf(
				"glyph", glyph,
				"token", row.get("token"),
				"unicode", row.get("unicode"),
				"kind", kind,
				"is_intermediate_component", intermediate,
				"display_title", intermediate ? "Intermediate component" : row.get("k_definition"),
				"display_structure", intermediate ? glyph : null,
				"definition", row.get("k_definition"),
				"mandarin", row.get("k_mandarin"),
				"stroke_count", row.get("stroke_count"),
				"decomp_type", row.get("decomp_type"),
				"decomp_label", IdsLabels.labelFor(stringValue(row.get("decomp_type"))),
				"depth", depth,
				"components", new ArrayList<Map<String, Object>>());

		if (depth >= 8 || components.isEmpty() || seen.contains(token)) {
			return node;
		}

		Set<String> nextSeen = new LinkedHashSet<>(seen);
		nextSeen.add(token);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> childNodes = (List<Map<String, Object>>) node.get("components");
		for (int position = 0; position < components.size(); position++) {
			String component = components.get(position);
			Map<String, Object> child = lookupComponent(conn, component);
			if (child == null) {
				boolean childIntermediate = IdsLabels.isIdsExpression(component);
				childNodes.add(mapOf(
						"glyph", component,
						"token", component,
						"kind", "unknown",
						"is_intermediate_component", childIntermediate,
						"display_title", childIntermediate ? "Intermediate component" : null,
						"display_structure", childIntermediate ? component : null,
						"depth", depth + 1,
						"position", position,
						"components", new ArrayList<Map<String, Object>>()));
				continue;
			}
			Map<String, Object> childNode = treeForRow(conn, child, depth + 1, nextSeen);
			childNode.put("position", position);
			childNodes.add(childNode);
		}
		return node;
	}

	private List<Map<String, Object>> baseComponentsFromTree(Map<String, Object> tree) {
		Map<String, Map<String, Object>> leaves = new LinkedHashMap<>();
		visitBaseComponents(tree, leaves);
		List<Map<String, Object>> values = new ArrayList<>(leaves.values());
		values.sort(Comparator
				.comparingInt((Map<String, Object> item) -> intValue(item.get("depth")))
				.thenComparingInt(item -> intValue(item.get("position")))
				.thenComparing(item -> Objects.toString(item.get("glyph") != null ? item.get("glyph") : item.get("token"), "")));
		return values;
	}

	private List<Map<String, Object>> dedupeComponents(List<Map<String, Object>> components) {
		Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
		for (Map<String, Object> component : components) {
			String key = Objects.toString(component.get("glyph"), "") + "\u0000" + Objects.toString(component.get("token"), "");
			deduped.putIfAbsent(key, component);
		}
		return new ArrayList<>(deduped.values());
	}

	@SuppressWarnings("unchecked")
	private void visitBaseComponents(Map<String, Object> node, Map<String, Map<String, Object>> leaves) {
		List<Map<String, Object>> children = (List<Map<String, Object>>) node.getOrDefault("components", List.of());
		if (intValue(node.get("depth")) > 0 && children.isEmpty()) {
			String key = Objects.toString(node.get("glyph"), "") + "\u0000" + Objects.toString(node.get("token"), "");
			leaves.put(key, mapOf(
					"glyph", node.get("glyph"),
					"token", node.get("token"),
					"position", node.get("position"),
					"depth", node.get("depth"),
					"definition", node.get("definition"),
					"mandarin", node.get("mandarin")));
		}
		for (Map<String, Object> child : children) {
			visitBaseComponents(child, leaves);
		}
	}

	private List<String> parseComponents(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return MAPPER.readValue(value, STRING_LIST).stream()
					.filter(item -> item != null && !item.isBlank())
					.toList();
		} catch (JsonProcessingException ignored) {
			return List.of();
		}
	}

	private List<Map<String, Object>> queryRows(String sql, List<Object> params) throws SQLException {
		try (Connection conn = connect();
				PreparedStatement statement = conn.prepareStatement(sql)) {
			bind(statement, params);
			try (ResultSet rows = statement.executeQuery()) {
				List<Map<String, Object>> results = new ArrayList<>();
				while (rows.next()) {
					results.add(rowToMap(rows));
				}
				return results;
			}
		}
	}

	private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
		for (int i = 0; i < params.size(); i++) {
			statement.setObject(i + 1, params.get(i));
		}
	}

	private static Map<String, Object> rowToMap(ResultSet rows) throws SQLException {
		ResultSetMetaData meta = rows.getMetaData();
		Map<String, Object> row = new LinkedHashMap<>();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			row.put(meta.getColumnLabel(i), rows.getObject(i));
		}
		return row;
	}

	private static String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private static int intValue(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(value.toString());
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static Map<String, Object> mapOf(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}
}
