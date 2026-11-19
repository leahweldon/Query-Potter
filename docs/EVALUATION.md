# Evaluation

## Setup

- **Collection:** TREC Disks 4 & 5 (minus the Congressional Record): *Financial Times* (1991–94), *Federal Register* (1994), *FBIS* (1996) and *Los Angeles Times* (1989–90).
- **Topics:** TREC-8 ad hoc topics 401–450, each with a title, a one-sentence description and a narrative describing what counts as relevant.
- **Judgements:** during development, relevance judgements were released for topics 401–425 only, so all reported results use those 25 topics.
- **Runs:** 1,000 ranked documents per topic, in TREC format.

## Metrics

- **MAP (mean average precision):** the main measure. It rewards ranking relevant documents highly across the whole result list.
- **P@20:** the fraction of the top 20 results that are relevant.
- **nDCG@20:** like P@20, but gives more credit for relevant documents nearer the top.

## Results

| Configuration | MAP | P@20 | nDCG@20 |
|---|---:|---:|---:|
| Title only (baseline) | 0.2437 | 0.422 | 0.4304 |
| Title + description | 0.2970 | 0.482 | 0.5183 |
| Title + description + cleaned narrative | 0.3162 | 0.506 | 0.5498 |
| Multi-query RRF | 0.3029 | 0.498 | 0.5446 |
| PRF ensemble | 0.3244 | 0.496 | 0.5474 |
| **Final system** | **0.3773** | **0.578** | **0.6460** |

The final system also reached **P@5 = 0.728**. All runs use BM25 with k1 = 0.5 and b = 1.0, the strongest settings found in a sweep of k1 and b. BM25 outperformed the Dirichlet language model in every query configuration we tested.

Adding the description gave the biggest early jump (+22% MAP over title only). Cleaning the narrative added a further gain, and RRF on its own improved early precision more than MAP. The final configuration combines cleaned narrative, multi-scale title weighting, PRF and RRF, so each row reflects several changes together rather than isolating a single technique.

## Limitations

- **Development topics.** Results are on the 25 topics that had judgements during development, so they show how well the system was tuned to those topics rather than how it would perform on new ones.
- **Topic-specific rules.** The final query mode (`PRF_TITLE_DESC_NARRATIVE_CLEANED`) includes hand-written adjustments for five judged topics (401, 405, 409, 419 and 421), such as excluding "nuclear" from topic 421 as its narrative specifies. These lift the development score and wouldn't generalise to unseen topics.
- **English only, lexical only.** The system uses English stemming and stopwords, and no learned or neural ranking.

## Reproducing the scores

With the collection, topics and qrels in place:

```bash
java -jar target/cs7is3-search-1.0.0.jar index --docs /path/to/collections --index index
java -jar target/cs7is3-search-1.0.0.jar search --index index --topics /path/to/topics --output runs/final.run

python3 tools/evaluate.py /path/to/qrels runs/final.run --out_csv out/results.csv --out_md out/results.md
python3 tools/evaluate_per_topic.py /path/to/qrels runs/final.run --out_csv out/per_topic.csv --out_md out/per_topic.md
```

The per-topic script is the most useful for diagnosis: it shows which topics a change helped or hurt, rather than only the average.
