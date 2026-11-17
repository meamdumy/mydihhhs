#!/usr/bin/env python3
"""Converts the World English Bible JSON (npm package `world-english-bible`)
into the compact text files the app bundles under app/src/main/assets/bible.

Usage:
    npm pack world-english-bible && tar xzf world-english-bible-*.tgz
    python3 tools/build_bible_data.py package/json app/src/main/assets/bible

Output:
    books.tsv   one line per book:  <file id>\t<name>\t<OT|NT>\t<chapter count>
    <id>.txt    one file per book, one record per line:
                    C<chapter number>
                    H<heading text>
                    V<verse number>\t<p|->\t<text>
                "p" marks a verse that opens a paragraph or stanza. A literal
                backslash-n inside the text is a line break within the verse
                (poetry lines).

To ship a different translation, produce the same files from another source.
"""
import json
import os
import re
import sys

BOOKS = [
    ("Genesis", "OT"), ("Exodus", "OT"), ("Leviticus", "OT"), ("Numbers", "OT"),
    ("Deuteronomy", "OT"), ("Joshua", "OT"), ("Judges", "OT"), ("Ruth", "OT"),
    ("1 Samuel", "OT"), ("2 Samuel", "OT"), ("1 Kings", "OT"), ("2 Kings", "OT"),
    ("1 Chronicles", "OT"), ("2 Chronicles", "OT"), ("Ezra", "OT"),
    ("Nehemiah", "OT"), ("Esther", "OT"), ("Job", "OT"), ("Psalms", "OT"),
    ("Proverbs", "OT"), ("Ecclesiastes", "OT"), ("Song of Solomon", "OT"),
    ("Isaiah", "OT"), ("Jeremiah", "OT"), ("Lamentations", "OT"),
    ("Ezekiel", "OT"), ("Daniel", "OT"), ("Hosea", "OT"), ("Joel", "OT"),
    ("Amos", "OT"), ("Obadiah", "OT"), ("Jonah", "OT"), ("Micah", "OT"),
    ("Nahum", "OT"), ("Habakkuk", "OT"), ("Zephaniah", "OT"), ("Haggai", "OT"),
    ("Zechariah", "OT"), ("Malachi", "OT"),
    ("Matthew", "NT"), ("Mark", "NT"), ("Luke", "NT"), ("John", "NT"),
    ("Acts", "NT"), ("Romans", "NT"), ("1 Corinthians", "NT"),
    ("2 Corinthians", "NT"), ("Galatians", "NT"), ("Ephesians", "NT"),
    ("Philippians", "NT"), ("Colossians", "NT"), ("1 Thessalonians", "NT"),
    ("2 Thessalonians", "NT"), ("1 Timothy", "NT"), ("2 Timothy", "NT"),
    ("Titus", "NT"), ("Philemon", "NT"), ("Hebrews", "NT"), ("James", "NT"),
    ("1 Peter", "NT"), ("2 Peter", "NT"), ("1 John", "NT"), ("2 John", "NT"),
    ("3 John", "NT"), ("Jude", "NT"), ("Revelation", "NT"),
]

TEXT_TYPES = ("paragraph text", "line text")
BLOCK_STARTS = ("paragraph start", "stanza start", "break")


def clean(text):
    """Collapses runs of whitespace, including around in-verse line breaks."""
    lines = [re.sub(r"[ \t\u00a0]+", " ", part).strip() for part in text.split("\n")]
    # A footnote marker removed by the source can leave a space before the
    # punctuation that followed it.
    lines = [re.sub(r"(?<=\w) (?=[,.;:!?])", "", line) for line in lines]
    return "\n".join(line for line in lines if line)


def convert_book(items):
    """Returns the output lines for one book and its chapter count."""
    chapters = []          # list of (number, [records])
    pending_headings = []
    block_pending = False  # a paragraph/stanza started since the last text
    break_pending = False  # a line break occurred since the last text
    verse = None           # [number, flag, text] being accumulated

    def flush():
        nonlocal verse
        if verse is not None:
            text = clean(verse[2])
            if text:
                chapters[-1][1].append(
                    "V%d\t%s\t%s" % (verse[0], verse[1], text.replace("\n", "\\n")))
            verse = None

    for item in items:
        kind = item["type"]
        if kind in BLOCK_STARTS:
            block_pending = True
        elif kind == "line break":
            break_pending = True
        elif kind == "header":
            # Drop translator footnotes that the source appends after a "*".
            pending_headings.append(clean(re.sub(r"\s*\*.*$", "", item["value"])))
        elif kind in TEXT_TYPES:
            chapter, number = item["chapterNumber"], item["verseNumber"]
            new_chapter = not chapters or chapters[-1][0] != chapter
            if new_chapter or verse is None or verse[0] != number:
                flush()
                if new_chapter:
                    chapters.append((chapter, []))
                for heading in pending_headings:
                    chapters[-1][1].append("H" + heading)
                pending_headings = []
                verse = [number, "p" if block_pending else "-", item["value"]]
            else:
                joiner = "\n" if (block_pending or break_pending) else ""
                verse[2] += joiner + item["value"]
            block_pending = break_pending = False
    flush()

    lines = []
    for number, records in chapters:
        lines.append("C%d" % number)
        lines.extend(records)
    numbers = [number for number, _ in chapters]
    if numbers != list(range(1, len(numbers) + 1)):
        raise ValueError("chapters are not sequential: %r" % numbers)
    return lines, len(chapters)


def main(src, dst):
    os.makedirs(dst, exist_ok=True)
    index = []
    for name, testament in BOOKS:
        file_id = name.lower().replace(" ", "")
        with open(os.path.join(src, file_id + ".json"), encoding="utf-8") as f:
            lines, chapter_count = convert_book(json.load(f))
        with open(os.path.join(dst, file_id + ".txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        index.append("%s\t%s\t%s\t%d" % (file_id, name, testament, chapter_count))
    with open(os.path.join(dst, "books.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(index) + "\n")
    print("wrote %d books to %s" % (len(index), dst))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
