"""Tests for build_bible_data.py. Run with: python3 -m unittest discover -s tools"""
import unittest

from build_bible_data import BOOKS, clean, convert_book


def text(kind, chapter, verse, value):
    return {"type": kind, "chapterNumber": chapter, "verseNumber": verse,
            "sectionNumber": 1, "value": value}


class CleanTest(unittest.TestCase):
    def test_collapses_whitespace(self):
        self.assertEqual("a b c", clean("  a   b\tc  "))

    def test_trims_around_line_breaks_and_drops_empty_lines(self):
        self.assertEqual("one\ntwo", clean(" one \n\n  two "))

    def test_removes_space_left_before_punctuation(self):
        self.assertEqual("under the nettles.", clean("under the nettles ."))
        self.assertEqual("light, and", clean("light , and"))

    def test_keeps_spacing_between_nested_quotation_marks(self):
        self.assertEqual("forever.” ’ ”", clean("forever.” ’ ”"))


class ConvertBookTest(unittest.TestCase):
    def test_prose_verses_and_paragraphs(self):
        lines, chapters = convert_book([
            {"type": "paragraph start"},
            text("paragraph text", 1, 1, "First.  "),
            text("paragraph text", 1, 2, "Second. "),
            {"type": "paragraph end"},
            {"type": "paragraph start"},
            text("paragraph text", 1, 3, "Third."),
            {"type": "paragraph end"},
        ])
        self.assertEqual(1, chapters)
        self.assertEqual(
            ["C1", "V1\tp\tFirst.", "V2\t-\tSecond.", "V3\tp\tThird."], lines)

    def test_poetry_lines_are_joined_with_an_escaped_break(self):
        lines, _ = convert_book([
            {"type": "stanza start"},
            text("line text", 1, 1, "Line one, "),
            {"type": "line break"},
            text("line text", 1, 1, "line two. "),
            {"type": "line break"},
            text("line text", 1, 2, "Next verse."),
            {"type": "stanza end"},
        ])
        self.assertEqual(
            ["C1", "V1\tp\tLine one,\\nline two.", "V2\t-\tNext verse."], lines)

    def test_verse_continuing_across_paragraphs_gets_a_break(self):
        lines, _ = convert_book([
            {"type": "paragraph start"},
            text("paragraph text", 1, 1, "He said, "),
            {"type": "paragraph end"},
            {"type": "paragraph start"},
            text("paragraph text", 1, 1, "“Go.”"),
            {"type": "paragraph end"},
        ])
        self.assertEqual(["C1", "V1\tp\tHe said,\\n“Go.”"], lines)

    def test_heading_attaches_to_the_chapter_it_introduces(self):
        lines, chapters = convert_book([
            {"type": "paragraph start"},
            text("paragraph text", 1, 1, "End of one."),
            {"type": "paragraph end"},
            {"type": "header", "value": "A Psalm by David.* A footnote."},
            {"type": "stanza start"},
            text("line text", 2, 1, "Start of two."),
            {"type": "stanza end"},
        ])
        self.assertEqual(2, chapters)
        self.assertEqual(
            ["C1", "V1\tp\tEnd of one.", "C2", "HA Psalm by David.", "V1\tp\tStart of two."],
            lines)

    def test_rejects_chapters_out_of_sequence(self):
        with self.assertRaises(ValueError):
            convert_book([text("paragraph text", 2, 1, "No chapter one.")])


class BookListTest(unittest.TestCase):
    def test_lists_the_sixty_six_books_in_order(self):
        self.assertEqual(66, len(BOOKS))
        self.assertEqual(39, sum(1 for _, testament in BOOKS if testament == "OT"))
        self.assertEqual(("Genesis", "OT"), BOOKS[0])
        self.assertEqual(("Matthew", "NT"), BOOKS[39])
        self.assertEqual(("Revelation", "NT"), BOOKS[-1])


if __name__ == "__main__":
    unittest.main()
