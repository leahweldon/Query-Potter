package org.cs7is3.parsing;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

class ForeignBroadcastParser implements DocumentsParser.DocumentParser {
    @Override
    public DocumentsParser.Document parse(String rawContent) {
        var document = Jsoup.parse(rawContent, Parser.xmlParser());

        var number = document.getElementsByTag("DOCNO").first().text();
        var title = document.getElementsByTag("TI").text();
        // This isn't really the author and is more like the source of the document in a serial number / code. It might
        // be useful but probably not.
        var author = document.getElementsByTag("AU").text();
        var body = document.getElementsByTag("TEXT").text();
        var date = document.getElementsByTag("DATE1").text();

        return new DocumentsParser.Document(number, title, author, body, date);
    }

    @Override
    public Set<String> ignoredFiles() {
        return Set.of("readchg.txt", "readmefb.txt");
    }

    @Override
    public Charset charset() {
        return StandardCharsets.ISO_8859_1;
    }
}
