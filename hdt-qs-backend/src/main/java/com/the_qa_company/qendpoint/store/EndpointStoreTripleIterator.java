package com.the_qa_company.qendpoint.store;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryInterruptedException;
import org.eclipse.rdf4j.sail.SailException;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TripleID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class EndpointStoreTripleIterator implements CloseableIteration<Statement, QueryEvaluationException> {
	private static final Logger logger = LoggerFactory.getLogger(EndpointStoreTripleIterator.class);

	private final AtomicBoolean closed = new AtomicBoolean();
	private final EndpointStore endpoint;
	private final EndpointTripleSource endpointTripleSource;
	private final IteratorTripleID iterator;
	private final CloseableIteration<? extends Statement, SailException> repositoryResult;
	private Statement next;

	public EndpointStoreTripleIterator(EndpointStore endpoint, EndpointTripleSource endpointTripleSource,
			IteratorTripleID iter, CloseableIteration<? extends Statement, SailException> repositoryResult) {
		this.endpoint = Objects.requireNonNull(endpoint, "endpoint can't be null!");
		this.endpointTripleSource = Objects.requireNonNull(endpointTripleSource, "endpointTripleSource can't be null!");
		this.iterator = Objects.requireNonNull(iter, "iter can't be null!");
		this.repositoryResult = Objects.requireNonNull(repositoryResult, "repositoryResult can't be null!");
	}

	@Override
	public boolean hasNext() {
		if (next != null) {
			return true;
		}
		if (closed.get()) {
			throw new QueryInterruptedException("closed iterator");
		}
		// iterate over the result of hdt
		while (iterator.hasNext()) {
			TripleID tripleID = iterator.next();
			long index = iterator.getLastTriplePosition();
			if (!endpoint.getDeleteBitMap().access(index)) {
				Resource subject = endpoint.getHdtConverter().IdToSubjectHDTResource(tripleID.getSubject());
				IRI predicate = endpoint.getHdtConverter().IdToPredicateHDTResource(tripleID.getPredicate());
				Value object = endpoint.getHdtConverter().IdToObjectHDTResource(tripleID.getObject());
				if (logger.isTraceEnabled()) {
					logger.trace("From HDT   {} {} {} ", subject.stringValue(), predicate.stringValue(),
							object.stringValue());
				}
				next = endpointTripleSource.getValueFactory().createStatement(subject, predicate, object);
				return true;
			}
		}
		// iterate over the result of rdf4j
		if (this.repositoryResult.hasNext()) {
			Statement stm = repositoryResult.next();
			Resource newSubj = endpoint.getHdtConverter().rdf4jToHdtIDsubject(stm.getSubject());
			IRI newPred = endpoint.getHdtConverter().rdf4jToHdtIDpredicate(stm.getPredicate());
			Value newObject = endpoint.getHdtConverter().rdf4jToHdtIDobject(stm.getObject());
			next = endpointTripleSource.getValueFactory().createStatement(newSubj, newPred, newObject,
					stm.getContext());
			if (logger.isTraceEnabled()) {
				logger.trace("From RDF4j {} {} {}", next.getSubject().stringValue(), next.getPredicate().stringValue(),
						next.getObject().stringValue());
			}
			return true;
		}
		return false;
	}

	@Override
	public Statement next() {
		if (!hasNext()) {
			return null;
		}
		Statement stm = endpointTripleSource.getValueFactory().createStatement(next.getSubject(), next.getPredicate(),
				next.getObject(), next.getContext());
		next = null;
		return stm;
	}

	@Override
	public void remove() {
		throw new RuntimeException("not implemented");
	}

	@Override
	public void close() {
		if (!closed.get()) {
			try {
				closed.set(true);
			} finally {
				repositoryResult.close();
			}
		}
	}
}
