package org.cs7is3;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.cs7is3.parsing.TopicsParser;

import java.util.List;

// A QueryGenerator constructs queries for a given topic. These queries are then run against the index and combined
// using Reciprocal Rank Fusion into 1 single rank that is then used as the output for a single topic. Multiple queries
// may be returned or just a singleton.
interface QueryGenerator {
    List<Query> generateQueries(Analyzer analyzer, TopicsParser.Topic topic) throws ParseException;
}
