/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.query;

import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.ExtendedQueryBase;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * A filtered query wrapped around another query similar to {@link
 * org.apache.lucene.search.BooleanClause.Occur#FILTER} -- it scores as 0. Moreover, it will use
 * Solr's filter cache.
 */
public class FilterQuery extends ExtendedQueryBase {
  protected final Query q;

  public FilterQuery(Query q) {
    if (q == null) {
      this.q = new MatchNoDocsQuery();
      return;
    }
    this.q = q;
  }

  public Query getQuery() {
    return q;
  }

  @Override
  public int hashCode() {
    return q.hashCode() + 0xc0e65615;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FilterQuery)) return false;
    FilterQuery fq = (FilterQuery) obj;
    return this.q.equals(fq.q);
  }

  @Override
  public String toString(String field) {
    StringBuilder sb = new StringBuilder();
    sb.append("filter(");
    sb.append(q.toString(""));
    sb.append(')');
    return sb.toString();
  }

  @Override
  public void visit(QueryVisitor visitor) {
    q.visit(visitor.getSubVisitor(BooleanClause.Occur.FILTER, this));
  }

  @Override
  public Query rewrite(IndexReader reader) throws IOException {
    Query newQ = q.rewrite(reader);
    if (newQ != q) {
      return new FilterQuery(newQ);
    } else {
      return this;
    }
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    // SolrRequestInfo reqInfo = SolrRequestInfo.getRequestInfo();

    if (!(searcher instanceof SolrIndexSearcher)) {
      // delete-by-query won't have SolrIndexSearcher
      // note: CSQ has some optimizations so we wrap it even though unnecessary given 0 boost
      return new ConstantScoreQuery(q).createWeight(searcher, scoreMode, 0f);
    }

    SolrIndexSearcher solrSearcher = (SolrIndexSearcher) searcher;
    DocSet docs = solrSearcher.getDocSet(q);
    // reqInfo.addCloseHook(docs);  // needed for off-heap refcounting

    // note: DocSet.makeQuery is basically a CSQ
    return docs.makeQuery().createWeight(searcher, scoreMode, 0f);
  }
}
