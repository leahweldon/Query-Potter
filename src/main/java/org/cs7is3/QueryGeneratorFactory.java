package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.cs7is3.parsing.TopicsParser;

import java.util.List;

public class QueryGeneratorFactory {
    // A QueryGeneratorFactory constructs queries for a given topic. These queries are then run against the index and
    // combined using Reciprocal Rank Fusion into 1 single rank that is then used as the output for a single topic.
    // Multiple queries may be returned or just a singleton.
    interface QueryGenerator {
        List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException;
    }

    public enum QueryMode {
        BOOSTED_TITLE
    }

    static QueryGenerator getInstance(QueryMode mode) {
        return switch (mode) {
            case BOOSTED_TITLE -> new BoostedTitleQueryGenerator();
        };
    }

    private static final class BoostedTitleQueryGenerator implements QueryGenerator {
        private static final String[] FIELDS = {"TITLE", "AUTHOR", "BODY", "DATE"};

        @Override
        public List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException {
            var parser = new MultiFieldQueryParser(FIELDS, analyzer);
            var titleQuery = parser.parse(QueryParserBase.escape(topic.title()));
            var descriptionQuery = parser.parse(QueryParserBase.escape(topic.description()));
            var query = new BooleanQuery.Builder()
                    .add(new BoostQuery(titleQuery, 2.0f), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(descriptionQuery, 1.0f), BooleanClause.Occur.SHOULD)
                    .build();
            return List.of(query);
        }
    }
}
