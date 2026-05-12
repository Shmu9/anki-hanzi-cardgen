# Feature Requirements

## Product Goal

Build a web application that lets a user explore the generated character dictionary in `dictionary/dict.sqlite3`, create high-quality study materials from it, and push those materials into Anki with as little manual friction as possible.

The current dictionary scope is character-first: the source database stores glyph-level meanings, readings, stroke metadata, frequency/HSK metadata, and IDS-based decomposition data. The product should preserve a path to expand later into a word dictionary with example sentences, sentence audio, and richer media.

## Primary User Outcomes

- find any character dictionary entry quickly
- understand the structure, decomposition, radical/component breakdown, and metadata of an entry
- create flashcards from individual characters now, with a path to word cards later
- enrich cards with audio, stroke order, and mnemonics
- preserve a personalized way of understanding radicals and components
- review and edit cards before they reach Anki
- keep local drafts and Anki notes synchronized safely

## Users

### 1. Learner

Needs:

- fast lookup
- intuitive flashcard creation
- media-rich study cards
- minimal configuration
- mnemonic generation that matches how they personally think about radicals

### 2. Power User

Needs:

- precise search behavior
- editable templates and field mappings
- deck and tag control
- visibility into raw source data and sync state
- fine-grained control over component and radical interpretations used in mnemonic generation

### 3. Maintainer

Needs:

- source provenance
- parser/debug tooling
- ingestion job visibility
- cache and failure diagnostics

## Functional Requirements

## 0. Accounts and Personalization

The system must:

- support a user account for each learner
- persist user-specific study preferences separately from shared dictionary content
- let each user maintain preferred meanings for radicals and recurring components
- allow more than one preferred meaning per radical
- track which preference set was used to generate each mnemonic

The system should:

- allow users to rank alternate radical meanings
- allow users to maintain notes about why they prefer a given interpretation
- let users define multiple mnemonic generation profiles
- let users choose from sample mnemonic structure templates

## 0a. Authentication and Session Management

The system must:

- provide sign-in and registration flows
- keep the registration page separate from the sign-in page
- make the registration page reachable from the sign-in page
- validate email addresses with a server-side regex before account creation
- require each account email address to be unique
- require each display name to be unique
- require display names to be at least 3 characters and no more than 20 characters
- limit display names to a predictable character set such as letters, numbers, underscores, and hyphens
- require passwords to follow a strong-password ruleset
- require passwords to be at least 8 characters
- require passwords to include at least one number or symbol
- reject passwords that include the user's display name
- reject passwords that include any substring of length 5 or more from the user's email address
- clearly and dynamically show the password rules and whether the password entered so far satisfies each rule
- password should have toggleable view and hide
- replace the signed-out navigation section with a user profile, settings, or preferences tab after sign-in
- include a sign-out action in the user profile, settings, or preferences page
- expire sessions after 20 minutes of inactivity
- refresh the session inactivity expiry whenever the authenticated user performs an action
- warn the user near the 20-minute inactivity limit that their session is about to expire
- let the user extend their session from the expiry warning prompt
- enforce an absolute token lifetime of no more than 12 hours, even when activity keeps refreshing the inactivity expiry
- require re-authentication after the absolute token lifetime expires

The system should:

- normalize email addresses before uniqueness checks
- compare email uniqueness case-insensitively
- compare display name uniqueness case-insensitively
- reserve confusing or administrative display names such as `admin`, `support`, and `system`
- show clear inline validation errors during registration
- keep access tokens short-lived and rotate session tokens when the inactivity expiry is refreshed
- record sign-in, sign-out, failed sign-in, and session-expiry events for account diagnostics

The system must not:

- expose whether a given email address exists during sign-in failure messages
- rely only on client-side validation for email or display name rules
- allow a refreshed session to bypass the absolute token lifetime

## 1. Dictionary Search and Browse

The system must:

- search by Hanzi
- search by pinyin with tone numbers
- search by mixed input
- support direct glyph, Unicode, or internal token lookup
- display results with character, pronunciation, and a concise definition preview
- display full entry detail with normalized definition content
- display character metadata from `dict.sqlite3`, including available stroke count, radical-stroke data, frequency, HSK level, and source fields
- expose raw source data for debugging

The system should:

- support filters for HSK, stroke count, radical, frequency, and other extractable character metadata
- support exact, prefix, and fuzzy search modes
- show related characters, component characters, and characters that share a component
- allow future word-level search once a word dictionary source is added

## 2. Entry Rendering

The system must:

- render normalized glyph records from `dict.sqlite3`
- display Unihan-derived fields such as `k_definition`, `k_mandarin`, `k_japanese`, `k_hanyu_pinlu`, and radical-stroke fields when available
- render IDS decomposition data from `decomp_type` and `decomp_components`
- render a clean, readable entry detail view
- preserve access to the raw unparsed record

The system should:

- extract structured metadata from generated dictionary fields
- surface that metadata consistently in the UI
- make source provenance visible when fields come from Unihan, IDS decomposition data, HanziDB metadata, or local reviewed supplements

## 2a. Character Decomposition and Radical Visualization

The system must:

- visualize the selected character's immediate IDS decomposition
- visualize recursive base components using `glyph_base_component_summary`
- show component order, recursive depth, and available component meanings
- distinguish direct components from leaf/base components
- expose radical-stroke metadata when available
- let the user inspect the raw decomposition tree for debugging
- use the same decomposition data for mnemonic generation and flashcard enrichment

The system should:

- support an interactive tree or layered breakdown view
- show characters that reuse the selected component
- allow a user to override or annotate component meanings for mnemonic purposes
- preserve regional or source-specific component forms where the database provides them

## 3. Flashcard Drafting

The system must:

- let the user add one or more entries to a flashcard staging area
- generate default note fields from normalized dictionary data
- allow the user to edit every generated field
- support tagging and deck selection
- preview the resulting card before export or sync
- use the active user's mnemonic preferences when generating flashcard mnemonic content

The system should:

- support multiple note presets
- support bulk card generation from search results
- support card generation for individual characters inside a term
- support future card generation from word entries and example sentences when those sources are added
- let the user swap between mnemonic generation profiles before regenerating a mnemonic
- let the user switch mnemonic structure templates before regenerating a mnemonic

## 4. Media Enrichment

The system must:

- support attaching audio to cards
- support attaching stroke-order media for individual characters
- support attaching mnemonic text for individual characters
- support attaching decomposition or radical breakdown media/HTML to character cards
- store provenance and source type for every attached asset
- distinguish sourced assets from generated fallbacks

The system should:

- support word-level audio and example-sentence audio after the product expands beyond character entries
- show confidence or quality indicators for enriched content
- let the user replace a sourced asset manually
- allow per-card inclusion or exclusion of each enrichment

## 5. Audio Requirements

The system must:

- prefer website-sourced audio when available
- support TTS fallback when sourced audio is unavailable
- store whether audio was sourced or generated
- attach exported or synced audio in a way compatible with Anki
- support single-character audio for the current character dictionary scope

The system should:

- support word-level audio after a word dictionary source is added
- allow regeneration with a different TTS voice
- normalize audio format and volume for consistent playback

## 6. Stroke Order Requirements

The system must:

- support sourced stroke-order assets for individual characters
- cache stroke-order assets locally
- attach stroke-order content to card templates that support it

The system should:

- support multiple presentation formats such as SVG and animated media
- allow the user to omit stroke order on cards where it adds clutter

The system must not:

- invent stroke order with an LLM

## 7. Mnemonic Requirements

The system must:

- support sourced mnemonic content when available
- support LLM-generated mnemonic fallback when sourced content is unavailable
- clearly label generated mnemonics as synthetic
- allow manual editing of all mnemonic text
- generate mnemonics using the current user's saved radical/component meanings
- preserve the prompt inputs or a stable snapshot of the user preferences used
- support selectable mnemonic structure templates that influence prompt construction
- support retrying mnemonic generation runs
- enforce rate limits on mnemonic generation

The system must support examples such as:

- `郎` being generated from a user preference set where `良 = good, fine`
- the right-side `阝` being interpreted as `town / district` for one user
- the same `阝` being interpreted as `hill`, `slope`, `ear`, or another preferred label for a different user

The system should:

- generate multiple mnemonic candidates
- let the user save a preferred mnemonic per character
- ground generated mnemonics in the entry meaning and character metadata
- use pronunciation hints when they improve memorability
- let the user choose whether alternate radical meanings can be considered during generation
- provide curated mnemonic structure examples such as scene-based, component-by-component, sound-hook, and compact cues
- show why a generation request is blocked when a rate limit is hit
- allow manual retry after transient failures

The system must not:

- flatten all users into one shared radical glossary for mnemonic generation

## 8. Deck Management

The system must:

- let the user select or configure a target deck
- let the user select or configure a note model
- allow manual edits to drafts before sync
- track which drafts have already been synced

The system should:

- support duplicate detection
- support retagging and field updates for already-synced notes
- support local grouping of drafts into deck batches

## 9. Anki Integration

The system must:

- integrate with local desktop Anki through AnkiConnect
- create notes in chosen decks
- update existing notes when the user approves it
- attach media assets required by the chosen note template

The system should:

- detect when AnkiConnect is unavailable and degrade gracefully
- provide actionable sync errors
- support a manual export fallback

The system must not:

- claim direct browser-based editing of AnkiWeb unless a real supported integration is added later

## 10. Export Requirements

The system must:

- support CSV export for card data
- support exporting associated media files in a stable structure

The system should:

- support packaged deck export later
- include a manifest describing exported assets and provenance

## 11. Caching Requirements

The system must:

- cache normalized dictionary entries
- cache rendered decomposition trees and component summaries
- cache search results
- cache downloaded media assets
- cache generated TTS output
- cache generated mnemonic candidates
- cache user-specific mnemonic generations separately when their radical preferences differ

The system should:

- invalidate caches when parser or source versions change
- deduplicate media by checksum
- expose cache status in admin views

## 12. Background Jobs

The system must:

- process slow enrichment tasks asynchronously
- expose job status and failures
- support retrying failed jobs
- support asynchronous mnemonic generation against a locally hosted model

The system should:

- allow bulk enrichment for selected characters or decks
- support canceling queued jobs
- expose retry counts and throttling reasons for mnemonic generation jobs

## 13. Admin and Diagnostics

The system must:

- show source database metadata
- show enrichment source health and recent failures
- show parser and cache version information
- show sync job history

The system should:

- expose raw API or query traces in a debug mode
- support reprocessing entries after parser changes

## Suggested Feature Enhancements

These are not mandatory for v1 but are high-value:

- example sentence support
- sentence audio generation
- word dictionary search and card generation
- user notes per entry or per character
- user-maintained radical gloss libraries with import/export
- bookmark and study list support
- saved searches
- conflict review workflow for changed synced notes
- card quality scoring based on completeness of enrichments

## Non-Functional Requirements

The system must:

- treat generated `dictionary/dict.sqlite3` as application source data and avoid mutating it at runtime
- store application-owned data separately
- remain usable with a large dictionary dataset
- provide deterministic card generation from the same source inputs
- keep user-specific mnemonic generations reproducible from the same radical preference snapshot and model version
- protect the local mnemonic generation service from overload with explicit rate limits and queueing

The system should:

- keep common search interactions responsive
- allow the user to continue drafting while background enrichment runs
- preserve auditability for scraped and generated content

## v1 Scope Recommendation

Recommended first release:

- character dictionary search and detail view
- normalized glyph record rendering
- character decomposition and radical/component visualization
- user accounts and radical preference storage
- flashcard staging and editing
- basic note template support
- local AnkiConnect sync
- sourced audio support
- stroke-order support for individual characters
- mnemonic support with manual entry and local Gemma 4 fallback
- core caches for search, entry normalization, and media

Deferred until later if necessary:

- word dictionary ingestion/search
- example sentences and sentence audio
- packaged deck export
- advanced duplicate reconciliation
- multi-provider ranking
- collaborative or shared mnemonic libraries
