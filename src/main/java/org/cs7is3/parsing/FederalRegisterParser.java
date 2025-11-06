package org.cs7is3.parsing;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.util.Set;

class FederalRegisterParser implements DocumentsParser.DocumentParser {
    @Override
    public DocumentsParser.Document parse(String rawContent) {
        var document = Jsoup.parse(rawContent, Parser.xmlParser());

        var number = document.getElementsByTag("DOCNO").first().text();
        var title = document.getElementsByTag("DOCTITLE").text();
        var author = document.getElementsByTag("SIGNER").text();
        var body = document.getElementsByTag("TEXT").text();
        var date = document.getElementsByTag("DATE").text();

        return new DocumentsParser.Document(number, title, author, body, date);
    }

    @Override
    public Set<String> ignoredFiles() {
        return Set.of("readchg", "readmefr");
    }
}
