# Contributions

Query Potter was built by Group 27 for CS7IS3 Information Retrieval & Web Search at Trinity College Dublin (October–December 2025). The final system and report are joint work; the summary below reflects each person's main areas, based on the commit history and the team's project journal.

| Contributor | Main areas |
|---|---|
| **Sam Taylor** | Project structure and CLI; configuration system; parsers for all four collections and the topics file; the query-generator framework; Reciprocal Rank Fusion; Dirichlet similarity; extended stopword list; parallel evaluation tooling and CI; late-stage PRF and RRF parameter tuning |
| **Leah Weldon** | Retrieval and query optimisation: pseudo-relevance feedback and phrase matching; title, description and cleaned-narrative query modes; BM25 parameter tuning; multi-scale title weighting; topic-level tuning and per-topic diagnostics; project journal; report methodology |
| **Tanya Tiwari** | Lucene indexing; analyzer configuration and stopword experiments |
| **Pranjal Yadav** | Early title/description boosting; multi-query variants; narrative filtering and robustness checks |
| **Sai Ruthwik Thummurugoti** | Experimental query variants; narrative-term extraction; auxiliary query generation |

Some work happened on feature branches or was shared in pairing and meetings, so the commit history on `main` doesn't capture everyone's contribution in full.

The project report is in [docs/report.pdf](docs/report.pdf).
