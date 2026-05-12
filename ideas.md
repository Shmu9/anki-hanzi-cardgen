- generate visualisation based off IDS_OPERATORS = {
    "⿰": 2,
    "⿱": 2,
    "⿲": 3,
    "⿳": 3,
    "⿴": 2,
    "⿵": 2,
    "⿶": 2,
    "⿷": 2,
    "⿸": 2,
    "⿹": 2,
    "⿺": 2,
    "⿻": 2,
}
- click through results pages (paginate searchResults)
- search via pinyin + number for tone -> any pronunciation of the character
- maybe customise meaning based on position:
    position_hint TEXT NOT NULL DEFAULT 'any'
        CHECK (position_hint IN ('any', 'left', 'right', 'top', 'bottom', 'inside', 'outside', 'phonetic', 'semantic')),
- include whether character is ideogrammatic, phonetic + semantic, pictogram
- if phonetic + semantic -> label each component correspondingly
- include_in_generation for including user-defined component meaning in mnemonic generation
- better regex for username + email + password
- multi-thread server + db
- lazy load (have some characters available by default for explore pages)
- lazy load preferences for component/character/radical on explore and dictionary pages + button to add new meaning
