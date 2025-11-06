package org.cs7is3.parsing;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.util.Set;

class FinancialTimesParser implements DocumentsParser.DocumentParser {
    @Override
    public DocumentsParser.Document parse(final String rawContent) {
        var document = Jsoup.parse(rawContent, Parser.xmlParser());

        // We only want 1 DOCNO per document, but the other fields may appear multiple times. In such cases, we want to
        // extract all occurrences of those fields.
        var number = document.getElementsByTag("DOCNO").first().text();
        var title = document.getElementsByTag("HEADLINE").text();
        var author = document.getElementsByTag("BYLINE").text();
        var body = document.getElementsByTag("TEXT").text();
        var date = document.getElementsByTag("DATE").text();

        return new DocumentsParser.Document(number, title, author, body, date);
    }

    @Override
    public Set<String> ignoredFiles() {
        return Set.of("readfrcg", "readmeft");
    }
}
