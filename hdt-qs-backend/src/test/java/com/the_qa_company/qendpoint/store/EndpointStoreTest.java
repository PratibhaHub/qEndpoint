package com.the_qa_company.qendpoint.store;

import com.the_qa_company.qendpoint.model.SimpleBNodeHDT;
import com.the_qa_company.qendpoint.utils.BitArrayDisk;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rdfhdt.hdt.exceptions.NotFoundException;
import org.rdfhdt.hdt.exceptions.ParserException;
import org.rdfhdt.hdt.hdt.HDT;
import org.rdfhdt.hdt.hdt.HDTManager;
import org.rdfhdt.hdt.options.HDTSpecification;
import org.rdfhdt.hdt.triples.IteratorTripleString;
import org.rdfhdt.hdt.triples.TripleString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EndpointStoreTest {
	private static final Logger logger = LoggerFactory.getLogger(EndpointStoreTest.class);
	public static final String HDT_INDEX_NAME = "index_tst.hdt";
	@Rule
	public TemporaryFolder tempDir = new TemporaryFolder();
	HDTSpecification spec;

	@Before
	public void setUp() {
		spec = new HDTSpecification();
		spec.setOptions("tempDictionary.impl=multHash;dictionary.type=dictionaryMultiObj;");
		MergeRunnableStopPoint.debug = true;
	}

	@After
	public void complete() throws InterruptedException {
		MergeRunnable.debugWaitMerge();
		MergeRunnableStopPoint.debug = false;
	}

	@Test
	public void testInstantiate() throws IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		EndpointStore endpoint = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", true);
		endpoint.shutDown();
	}

	@Test
	public void testGetConnection() throws IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		EndpointStore endpoint = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", true);
		SailConnection connection = endpoint.getConnection();
		connection.close();
		endpoint.shutDown();
	}

	@Test
	public void testSailRepository() throws IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		SailRepository endpointStore = new SailRepository(new EndpointStore(hdtStore.getAbsolutePath() + "/",
				HDT_INDEX_NAME, spec, nativeStore.getAbsolutePath() + "/", true));
		endpointStore.shutDown();
	}

	@Test
	public void testGetSailRepositoryConnection() throws IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		SailRepository endpointStore = new SailRepository(new EndpointStore(hdtStore.getAbsolutePath() + "/",
				HDT_INDEX_NAME, spec, nativeStore.getAbsolutePath() + "/", true)
		// new NativeStore(nativeStore,"spoc")
		);
		try (SailRepositoryConnection connection = endpointStore.getConnection()) {
			System.out.println(connection.size());
		}
		endpointStore.shutDown();
	}

	@Test
	public void testShutdownAndRecreate() throws IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");

		HDT hdt = Utility.createTempHdtIndex(tempDir, true, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		EndpointStore endpoint = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", true);

		try (NotifyingSailConnection connection = endpoint.getConnection()) {
			connection.begin();
			connection.addStatement(RDF.TYPE, RDF.TYPE, RDFS.RESOURCE);
			connection.commit();
		}
		endpoint.shutDown();
		endpoint = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", true);
		try (NotifyingSailConnection connection = endpoint.getConnection()) {
			connection.begin();
			connection.addStatement(RDF.TYPE, RDF.TYPE, RDFS.RESOURCE);
			connection.commit();
		}
		endpoint.shutDown();
		hdt.close();
	}

	@Test
	public void testAddStatement() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		SailRepository endpointStore = new SailRepository(new EndpointStore(hdtStore.getAbsolutePath() + "/",
				HDT_INDEX_NAME, spec, nativeStore.getAbsolutePath() + "/", true));

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			IRI dennis = vf.createIRI(ex, "Dennis");
			connection.add(dennis, RDF.TYPE, FOAF.PERSON);
			List<? extends Statement> statements = Iterations.asList(connection.getStatements(null, null, null, true));
			// one triple in hdt and 2 added to native = 3 triples
			statements.forEach(System.out::println);
			assertEquals(3, statements.size());
		}
	}

	@Test
	public void testMerge() throws InterruptedException, IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			IRI dennis = vf.createIRI(ex, "Dennis");
			connection.add(dennis, RDF.TYPE, FOAF.PERSON);

			// with given THRESHOLD = 2, the hdt index will be merged with all
			// triples from current native store
			IRI pierre = vf.createIRI(ex, "Pierre");
			connection.add(pierre, RDF.TYPE, FOAF.PERSON);

			IRI guo = vf.createIRI(ex, "Guo");
			connection.remove(guo, RDF.TYPE, FOAF.PERSON);
			// wait for merge to be done because it's on a separate thread

			RepositoryResult<Statement> sts = connection.getStatements(null, null, null, true);
			int count = 0;
			while (sts.hasNext()) {
				System.out.println(sts.next());
				count++;
			}
			// 1 triple hdt, 2 triples native a, 1 triple native b -1 triple
			// removed from hdt
			assertEquals(3, count);
			Thread.sleep(3000);

			sts = connection.getStatements(null, null, null, true);
			count = 0;
			while (sts.hasNext()) {
				System.out.println(sts.next());
				count++;
			}
			// 2 triples hdt, 0 triples native a, 1 triple native b
			assertEquals(3, count);
			Files.deleteIfExists(Paths.get(HDT_INDEX_NAME));
			Files.deleteIfExists(Paths.get(HDT_INDEX_NAME + ".index.v1-1"));
			Files.deleteIfExists(Paths.get("index.nt"));

		}
	}

	@Test
	public void testMergeBig() throws IOException, InterruptedException {
		MergeRunnableStopPoint.STEP2_END.debugLock();
		MergeRunnableStopPoint.STEP2_END.debugLockTest();
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");

		HDT hdt = Utility.createTempHdtIndex(tempDir, false, true, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		// printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);

		int toAdd = 15;
		int toDelete = Utility.COUNT / 100;
		BitArrayDisk deleted = new BitArrayDisk(Utility.COUNT);
		Random rnd = new Random(42);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			connection.add(Utility.getFakePersonStatement(vf, Utility.COUNT + toAdd - 2));
			connection.add(Utility.getFakePersonStatement(vf, Utility.COUNT + toAdd - 1));
			connection.add(Utility.getFakePersonStatement(vf, Utility.COUNT + toAdd));
			// should trigger merge event
		}

		MergeRunnableStopPoint.STEP2_END.debugWaitForEvent();

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();

			// delete toDelete persons from HDT
			connection.begin();
			for (int i = 0; i < toDelete; i++) {
				int id = rnd.nextInt(Utility.COUNT);
				connection.remove(Utility.getFakeStatement(vf, id));
				deleted.set(id, true);
			}
			connection.commit();
		}

		MergeRunnableStopPoint.STEP2_END.debugUnlockTest();

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();

			for (int i = 1; i <= toAdd - 3; i++) {
				connection.add(Utility.getFakePersonStatement(vf, Utility.COUNT + i));
			}

			int endCount = toAdd + Utility.COUNT - (int) deleted.countOnes();

			// wait for merge to be done because it's on a separate thread

			RepositoryResult<Statement> sts = connection.getStatements(null, null, null, true);
			int count = 0;
			while (sts.hasNext()) {
				sts.next();
				count++;
			}
			// 1 triple hdt, 2 triples native a, 1 triple native b -1 triple
			// removed from hdt
			assertEquals(endCount, count);
			Thread.sleep(3000);

			sts = connection.getStatements(null, null, null, true);
			count = 0;
			while (sts.hasNext()) {
				sts.next();
				count++;
			}
			// 2 triples hdt, 0 triples native a, 1 triple native b
			assertEquals(endCount, count);

		}
	}

	@Test
	@Ignore
	public void testMergeMultiple() throws IOException, NotFoundException, InterruptedException {

		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, true, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(999);
		SailRepository endpointStore = new SailRepository(store);

		RepositoryConnection connection = endpointStore.getConnection();
		for (int i = 0; i < 5; i++) {
			System.out.println("Merging phase: " + (i + 1));
			int count = 1000;
			connection = endpointStore.getConnection();
			connection.begin();
			for (int j = i * count; j < (i + 1) * count; j++) {
				connection.add(RDFS.RESOURCE, RDFS.LABEL, connection.getValueFactory().createLiteral(j));
			}
			connection.commit();
			System.out.println("Count before merge:" + connection.size());
			assertEquals(count * (i + 1), connection.size());
			Thread.sleep(4000);
			System.out.println("Count after merge:" + connection.size());
			assertEquals(count * (i + 1), connection.size());
		}
		assertEquals(5000, connection.size());

	}

	@Test
	public void testCommonNativeAndHdt() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(10);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			IRI dennis = vf.createIRI(ex, "Dennis");
			connection.add(dennis, RDF.TYPE, FOAF.PERSON);

			// query everything of type PERSON
			List<? extends Statement> statements = Iterations
					.asList(connection.getStatements(null, RDF.TYPE, FOAF.PERSON, true));
			for (Statement s : statements) {
				System.out.println(s);
			}
			// 1 triple in hdt and 2 added to native = 3 triples
			assertEquals(3, statements.size());
		}
	}

	@Test
	public void rdf4jUsedWorkflow() throws IOException {
		// not really a test, more code workflow that is used internally as one
		// store and that can be used to debug
		File dir = new File(tempDir.newFolder("native-store"), "A");
		try {
			FileUtils.deleteDirectory(dir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (dir.mkdirs()) {
			logger.debug("{} created.", dir);
		}
		NativeStore nativeStore = new NativeStore(dir, "spoc,posc,cosp");

		NotifyingSailConnection connection = nativeStore.getConnection();

		connection.begin();
		connection.startUpdate(null);

		ValueFactory factory = SimpleValueFactory.getInstance();
		for (int i = 0; i < 5000; i++) {
			IRI s1 = factory.createIRI("http://s" + i);
			IRI p1 = factory.createIRI("http://p" + i);
			IRI o1 = factory.createIRI("http://o" + i);
			connection.addStatement(s1, p1, o1);
		}
		connection.endUpdate(null);
		connection.commit();
		connection.close();

		SailRepository repository = new SailRepository(nativeStore);

		RepositoryConnection connection2 = repository.getConnection();
		String sparqlQuery = "SELECT ?s WHERE { ?s  <http://p1> <http://o1> . } ";
		TupleQuery tupleQuery1 = connection2.prepareTupleQuery(sparqlQuery);
		TupleQueryResult tupleQueryResult = tupleQuery1.evaluate();

		assertTrue(tupleQueryResult.hasNext());

		if (tupleQueryResult.hasNext()) {
			tupleQueryResult.stream().iterator().forEachRemaining(System.out::println);
		}
	}

	private static class StatementComparator implements Comparator<Statement> {

		@Override
		public int compare(Statement o1, Statement o2) {
			if (o1.getSubject().toString().compareTo(o2.getSubject().toString()) == 0) {
				if (o1.getPredicate().toString().compareTo(o2.getPredicate().toString()) == 0) {
					if (o1.getObject().toString().compareTo(o2.getObject().toString()) == 0) {
						return 0;
					} else {
						return o1.getObject().toString().compareTo(o2.getObject().toString());
					}
				} else {
					return o1.getPredicate().toString().compareTo(o2.getPredicate().toString());
				}
			} else {
				return o1.getSubject().toString().compareTo(o2.getSubject().toString());
			}
		}
	}

	private void compareTriples(List<? extends Statement> stmtsAdded, List<? extends Statement> stmtsQueried) {
		stmtsQueried.sort(new StatementComparator());
		stmtsAdded.sort(new StatementComparator());

		for (int i = 0; i < stmtsAdded.size(); i++) {
			Statement stm1 = stmtsAdded.get(i);
			Statement stm2 = stmtsQueried.get(i);

			if (!(stm1.getSubject().equals(stm2.getSubject()) && stm1.getPredicate().equals(stm2.getPredicate())
					&& stm1.getObject().equals(stm2.getObject()))) {

				fail("Not equal: [" + stm1 + "] - [" + stm2 + "]");
			}
		}
	}

	@Test
	@Ignore
	public void testIndexGradually() throws InterruptedException, IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, true, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(99);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ClassLoader classLoader = getClass().getClassLoader();
			URL cocktails = classLoader.getResource("cocktails.nt");
			Assert.assertNotNull(cocktails);
			try (InputStream inputStream = new FileInputStream(cocktails.getFile())) {
				RDFParser rdfParser = Rio.createParser(RDFFormat.NTRIPLES);
				rdfParser.getParserConfig().set(BasicParserSettings.VERIFY_URI_SYNTAX, false);
				try (GraphQueryResult res = QueryResults.parseGraphBackground(inputStream, null, rdfParser,
						new WeakReference<>(this))) {
					int count = 1;
					ArrayList<Statement> stmtsAdded = new ArrayList<>();
					while (res.hasNext()) {
						Statement st = res.next();
						stmtsAdded.add(st);
						connection.add(st);
						if (count % 100 == 0) {
							System.out.println("Sleeping for 2s...");
							List<? extends Statement> statements = Iterations
									.asList(connection.getStatements(null, null, null));
							compareTriples(stmtsAdded, statements);
						}
						count++;
					}
					Thread.sleep(2000);
				}
			}
		}
	}

	@Test
	public void testDelete() throws InterruptedException, NotFoundException, IOException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);
		List<? extends Statement> statements;
		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = new MemValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Dennis"), RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Pierre"), RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Clement"), RDF.TYPE, FOAF.PERSON);
			Thread.sleep(2000);
			IRI guo = vf.createIRI(ex, "Guo");
			connection.remove(guo, RDF.TYPE, FOAF.PERSON);
			connection.remove(ali, RDF.TYPE, FOAF.PERSON);
			// query everything of type PERSON
			try (RepositoryResult<Statement> it = connection.getStatements(null, null, null, true)) {
				statements = Iterations.asList(it);
				for (Statement s : statements) {
					System.out.println(s.toString());
				}
			}
		}
		assertEquals(3, statements.size());
	}

	@Test
	public void testIDsConversion() throws IOException, NotFoundException, InterruptedException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		System.out.println("HDT content");
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);
		ArrayList<IRI> subjects = new ArrayList<>();
		ValueFactory vf = new MemValueFactory();
		String ex = "http://example.com/";

		subjects.add(vf.createIRI(ex, "Dennis"));
		subjects.add(vf.createIRI(ex, "Pierre"));
		subjects.add(vf.createIRI(ex, "Guo"));

		try (RepositoryConnection connection = endpointStore.getConnection()) {

			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Dennis"), RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Pierre"), RDF.TYPE, FOAF.PERSON);
			IRI guo = vf.createIRI(ex, "Guo");
			connection.remove(guo, RDF.TYPE, FOAF.PERSON);

			connection.remove(ali, RDF.TYPE, FOAF.PERSON);

			connection.add(guo, RDF.TYPE, FOAF.PERSON);
			Thread.sleep(5000);
			// query everything of type PERSON
			List<? extends Statement> statements = Iterations.asList(connection.getStatements(null, null, null, true));
			int index = 0;
			System.out.println(statements.size());
			for (Statement s : statements) {
				System.out.println("here " + s.toString());
				assertEquals(subjects.get(index).toString(), s.getSubject().toString());
				index++;
			}
			connection.close();
			assertEquals(3, statements.size());

		}

	}

	@Test
	public void testDeleteWhileMerge() throws IOException, NotFoundException, InterruptedException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = new MemValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Dennis"), RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Pierre"), RDF.TYPE, FOAF.PERSON);
			connection.add(vf.createIRI(ex, "Clement"), RDF.TYPE, FOAF.PERSON);
			IRI guo = vf.createIRI(ex, "Guo");
			connection.remove(guo, RDF.TYPE, FOAF.PERSON);
			connection.remove(ali, RDF.TYPE, FOAF.PERSON);
			// query everything of type PERSON
			List<? extends Statement> statements = Iterations.asList(connection.getStatements(null, null, null, true));
			for (Statement s : statements) {
				System.out.println(s.toString());
			}
			assertEquals(3, statements.size());
			Thread.sleep(3000);
			System.out.println("After merge:");
			statements = Iterations.asList(connection.getStatements(null, null, null, true));
			for (Statement s : statements) {
				System.out.println(s.toString());
			}
			assertEquals(3, statements.size());
		}
	}

	@Test
	public void sparqlTest() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(10);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			IRI dennis = vf.createIRI(ex, "Dennis");
			connection.add(dennis, RDF.TYPE, FOAF.PERSON);

			TupleQuery tupleQuery = connection.prepareTupleQuery(
					String.join("\n", "", "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
							"PREFIX foaf: <http://xmlns.com/foaf/0.1/>", "select ?s where {",
							"	?s rdf:type foaf:Person .", "}"));

			List<BindingSet> bindingSets = Iterations.asList(tupleQuery.evaluate());
			for (BindingSet binding : bindingSets) {
				System.out.println(binding);
			}
			assertEquals(3, bindingSets.size());
			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME));
			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME + ".index.v1-1"));
			Files.deleteIfExists(Paths.get("index.nt"));
		}
	}

	@Test
	public void sparqlDeleteTest() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);

			Update update = connection
					.prepareUpdate(String.join("\n", "", "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
							"PREFIX foaf: <http://xmlns.com/foaf/0.1/>", "PREFIX ex: <http://example.com/>",
							"DELETE DATA{", "	ex:Guo rdf:type foaf:Person .", "}"));

			update.execute();
			List<Statement> statements = Iterations.asList(connection.getStatements(null, null, null, (Resource) null));
			assertEquals(1, statements.size());
			for (Statement s : statements) {
				System.out.println(s);
				assertEquals(ali.toString(), s.getSubject().toString());
			}

			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME));
			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME + ".index.v1-1"));
			Files.deleteIfExists(Paths.get("index.nt"));

		}
	}

	@Test
	public void sparqlDeleteAllTest() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, true, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);

			Update update = connection
					.prepareUpdate(String.join("\n", "", "DELETE {", "	?s ?p ?o", "}", "\n", "WHERE { ?s ?p ?o}"));

			update.execute();
			List<Statement> statements = Iterations.asList(connection.getStatements(null, null, null, (Resource) null));
			assertEquals(0, statements.size());
		}
	}

	@Test
	public void sparqlJoinTest() throws IOException, NotFoundException {
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(2);
		SailRepository endpointStore = new SailRepository(store);

		try (RepositoryConnection connection = endpointStore.getConnection()) {
			ValueFactory vf = connection.getValueFactory();
			String ex = "http://example.com/";
			IRI ali = vf.createIRI(ex, "Ali");
			connection.add(ali, RDF.TYPE, FOAF.PERSON);
			IRI guo = vf.createIRI(ex, "Guo");
			IRI has = vf.createIRI(ex, "has");
			connection.add(guo, has, FOAF.ACCOUNT);

			TupleQuery tupleQuery = connection.prepareTupleQuery(
					String.join("\n", "", "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>",
							"PREFIX foaf: <http://xmlns.com/foaf/0.1/>", "PREFIX ex: <http://example.com/>",
							"select ?s where {", "	?s rdf:type foaf:Person .", "	?s ex:has foaf:account .",

							"}"));

			List<BindingSet> bindingSets = Iterations.asList(tupleQuery.evaluate());
			for (BindingSet binding : bindingSets) {
				System.out.println(binding);
			}
			assertEquals(1, bindingSets.size());
			connection.close();
			endpointStore.shutDown();
			Files.deleteIfExists(Paths.get(HDT_INDEX_NAME));
			Files.deleteIfExists(Paths.get(HDT_INDEX_NAME + ".index.v1-1"));
			Files.deleteIfExists(Paths.get("index.nt"));
		}
	}

	@Test
	public void testAddLargeDataset() throws IOException, NotFoundException {
		StopWatch stopWatch = StopWatch.createStarted();
		File nativeStore = tempDir.newFolder("native-store");
		File hdtStore = tempDir.newFolder("hdt-store");
		HDT hdt = Utility.createTempHdtIndex(tempDir, false, false, spec);
		assert hdt != null;
		hdt.saveToHDT(hdtStore.getAbsolutePath() + "/" + HDT_INDEX_NAME, null);
		printHDT(hdt);
		EndpointStore store = new EndpointStore(hdtStore.getAbsolutePath() + "/", HDT_INDEX_NAME, spec,
				nativeStore.getAbsolutePath() + "/", false);
		store.setThreshold(1000000);
		SailRepository endpointStore = new SailRepository(store);

		try (SailRepositoryConnection connection = endpointStore.getConnection()) {
			stopWatch.stop();

			stopWatch = StopWatch.createStarted();
			connection.begin();
			int count = 100000;
			for (int i = 0; i < count; i++) {
				connection.add(RDFS.RESOURCE, RDFS.LABEL, connection.getValueFactory().createLiteral(i));
			}
			connection.commit();
			stopWatch.stop();

			// Thread.sleep(2000);
			assertEquals(count + 1, connection.size());

			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME));
			Files.deleteIfExists(Paths.get(EndpointStoreTest.HDT_INDEX_NAME + ".index.v1-1"));
			Files.deleteIfExists(Paths.get("index.nt"));

		}

	}

	@Test
	public void bnodeTest() throws ParserException, IOException {
		Path dir = tempDir.newFolder().toPath();
		Path nativeStore = dir.resolve("native");
		Path hdtStore = dir.resolve("hdt");
		Files.createDirectories(hdtStore);
		try (HDT hdt = HDTManager.generateHDT(
				List.of(new TripleString("_:aaaa", "http://pppp", "\"aaaa\"^^<http://type>")).iterator(),
				Utility.EXAMPLE_NAMESPACE, spec, null)) {
			hdt.saveToHDT(hdtStore.resolve("test.hdt").toAbsolutePath().toString(), null);
		}

		EndpointStore store = new EndpointStore(new EndpointFiles(nativeStore, hdtStore, "test.hdt"), spec, false,
				true);
		HDTConverter converter = store.getHdtConverter();
		Resource bnode = converter.IdToSubjectHDTResource(1L);
		Assert.assertTrue(bnode instanceof BNode);
		Assert.assertTrue(bnode instanceof SimpleBNodeHDT);
		Assert.assertEquals(1L, ((SimpleBNodeHDT) bnode).getHdtId());
		Assert.assertEquals("aaaa", ((BNode) bnode).getID());
		Assert.assertEquals("_:aaaa", bnode.toString());
		SailRepository repo = new SailRepository(store);
		try (SailRepositoryConnection connection = repo.getConnection()) {

			ValueFactory vf = connection.getValueFactory();

			System.out.println(vf.createIRI(Utility.EXAMPLE_NAMESPACE + "test"));
			try (RepositoryResult<Statement> result = connection.getStatements(vf.createBNode("aaaa"),
					vf.createIRI("http://pppp"), vf.createLiteral("aaaa", vf.createIRI("http://type")))) {
				Assert.assertTrue(result.hasNext());
				result.next();
				Assert.assertFalse(result.hasNext());
			}
		} finally {
			repo.shutDown();
		}

	}

	private void printHDT(HDT hdt) throws NotFoundException {
		IteratorTripleString it = hdt.search("", "", "");
		while (it.hasNext()) {
			System.out.println(it.next());
		}
	}
}
