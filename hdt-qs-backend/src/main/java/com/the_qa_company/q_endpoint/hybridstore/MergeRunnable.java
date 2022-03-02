package com.the_qa_company.q_endpoint.hybridstore;

import com.github.jsonldjava.shaded.com.google.common.base.Stopwatch;

import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.common.concurrent.locks.Lock;
import org.eclipse.rdf4j.common.concurrent.locks.LockManager;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.rdfhdt.hdt.enums.RDFNotation;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.exceptions.ParserException;
import org.rdfhdt.hdt.hdt.HDT;
import org.rdfhdt.hdt.hdt.HDTManager;
import org.rdfhdt.hdt.hdt.HDTVersion;
import org.rdfhdt.hdt.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class MergeRunnable {
    /**
     * class to use {@link MergeThread}
     * @param <T> the merge thread type
     */
    @FunctionalInterface
    private interface MergeThreadRunnable<T> {
        /**
         * execute a merge step and the next step
         *
         * @param restarting if the step is restarting
         * @param data       if restarting == true, the return value of {@link MergeThreadReloader#reload()}
         * @throws InterruptedException
         * @throws IOException
         */
        void run(boolean restarting, T data) throws InterruptedException, IOException;
    }

    /**
     * class to use {@link MergeThread}
     * @param <T> the merge thread type
     */
    @FunctionalInterface
    private interface MergeThreadReloader<T> {
        /**
         * reload the previous merge data
         *
         * @return a lock if required by the step
         */
        T reload();
    }

    /////// this part is for testing purposes ///////
    // it extends the merging process to this amount of seconds. If -1 then it is not set.
    private static int extendsTimeMergeBeginning = -1;
    private static int extendsTimeMergeBeginningAfterSwitch = -1;
    private static int extendsTimeMergeEnd = -1;
    // If stopPoint != null, it will throw a MergeRunnableStopPoint#MergeRunnableStopException
    private static MergeRunnableStopPoint stopPoint;
    // store last merge exception
    private static final LockManager MERGE_THREAD_LOCK_MANAGER = new LockManager();
    // the last exception during the merge
    private static Exception debugLastMergeException;
    /////////////////////////////////////////////////


    public static int getExtendsTimeMergeBeginning() {
        return extendsTimeMergeBeginning;
    }

    public static int getExtendsTimeMergeBeginningAfterSwitch() {
        return extendsTimeMergeBeginningAfterSwitch;
    }

    public static int getExtendsTimeMergeEnd() {
        return extendsTimeMergeEnd;
    }

    public static void setExtendsTimeMergeBeginning(int extendsTimeMergeBeginning) {
        MergeRunnable.extendsTimeMergeBeginning = extendsTimeMergeBeginning;
    }

    public static void setExtendsTimeMergeBeginningAfterSwitch(int extendsTimeMergeBeginningAfterSwitch) {
        MergeRunnable.extendsTimeMergeBeginningAfterSwitch = extendsTimeMergeBeginningAfterSwitch;
    }

    public static void setExtendsTimeMergeEnd(int extendsTimeMergeEnd) {
        MergeRunnable.extendsTimeMergeEnd = extendsTimeMergeEnd;
    }

    /**
     * wait for the merge to complete, if the merge was stopped because of an exception
     * (except with {@link #setStopPoint(MergeRunnableStopPoint)} exception), it is thrown inside a RuntimeException
     * @throws InterruptedException
     */
    public static void debugWaitMerge() throws InterruptedException {
        MERGE_THREAD_LOCK_MANAGER.waitForActiveLocks();
        if (debugLastMergeException != null) {
            if (!(debugLastMergeException instanceof MergeRunnableStopPoint.MergeRunnableStopException)) {
                throw new RuntimeException(debugLastMergeException);
            }
        }
    }

    /**
     * set the next stop point in a merge, it would be used only once
     *
     * @param stopPoint the stop point
     */
    public static void setStopPoint(MergeRunnableStopPoint stopPoint) {
        MergeRunnable.stopPoint = stopPoint;
    }

    /**
     * try delete a file
     * @param file the file to delete
     */
    private static void delete(String file) {
        try {
            Files.delete(Paths.get(file));
        } catch (IOException e) {
            logger.warn("Can't delete the file {} ({})", file, e.getClass().getName());
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    /**
     * try delete a file if it exists
     * @param file the file to delete
     */
    private static void deleteIfExists(String file) {
        try {
            Files.deleteIfExists(Paths.get(file));
        } catch (IOException e) {
            logger.warn("Can't delete the file {} ({})", file, e.getClass().getName());
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    // .old file extension
    private static final String OLD_EXT = ".old";

    /**
     * delete "file + {@link #OLD_EXT}"
     * @param file file
     */
    private static void deleteOld(String file) {
        delete(file + OLD_EXT);
    }

    /**
     * test if the file exists
     * @param file the file
     * @return true if the file exists, false otherwise
     */
    private static boolean exists(String file) {
        return Files.exists(Paths.get(file));
    }
    /**
     * test if the file "file + {@link #OLD_EXT}" exists
     * @param file the file
     * @return true if the file exists, false otherwise
     */
    private static boolean existsOld(String file) {
        return exists(file + OLD_EXT);
    }

    /**
     * rename file to "file + {@link #OLD_EXT}"
     * @param file file
     */
    private static void renameToOld(String file) {
        rename(file, file + OLD_EXT);
    }

    /**
     * rename "file + {@link #OLD_EXT}" to file
     * @param file file
     */
    private static void renameFromOld(String file) {
        rename(file + OLD_EXT, file);
    }

    /**
     * rename a file to another
     * @param oldFile the current name
     * @param newFile the new name
     */
    private static void rename(String oldFile, String newFile) {
        try {
            Files.move(Paths.get(oldFile), Paths.get(newFile));
        } catch (IOException e) {
            logger.warn("Can't rename the file {} into {} ({})", oldFile, newFile, e.getClass().getName());
            e.printStackTrace();
//            throw new RuntimeException(e);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MergeRunnable.class);

    public class MergeThread<T> extends Thread {
        // actions of the merge
        private final MergeThreadRunnable<T> exceptionRunnable;
        private MergeThreadReloader<T> reloadData;
        private Runnable preload;
        // the data returned by the reloadData reloader
        private T data;
        // if the merge is a restarting one
        private final boolean restart;
        // lock for MergeRunnableStopPoint#debugWaitForEvent()
        private Lock debugLock;

        private MergeThread(MergeThreadRunnable<T> run, MergeThreadReloader<T> reloadData) {
            this(null, run, reloadData);
        }
        private MergeThread(Runnable preload, MergeThreadRunnable<T> run, MergeThreadReloader<T> reloadData) {
            this(run, true);
            this.reloadData = reloadData;
            this.preload = preload;
        }

        private MergeThread(MergeThreadRunnable<T> run, boolean restart) {
            super("MergeThread");
            this.exceptionRunnable = run;
            this.restart = restart;
        }

        /**
         * preload the data of the merge
         */
        public void preLoad() {
            if (preload != null)
                preload.run();
        }

        @Override
        public void run() {
            try {
                this.exceptionRunnable.run(restart, data);
            } catch (IOException e) {
                synchronized (MergeRunnable.this) {
                    hybridStore.setMerging(false);
                }
                if (MergeRunnableStopPoint.debug)
                    debugLastMergeException = e;
                e.printStackTrace();
            } catch (Exception e) {
                if (MergeRunnableStopPoint.debug)
                    debugLastMergeException = e;
                e.printStackTrace();
            } finally {
                if (MergeRunnableStopPoint.debug)
                    debugLock.release();
            }
        }

        @Override
        public synchronized void start() {
            if (MergeRunnableStopPoint.debug) {
                // create a lock to use the method MergeRunnableStopPoint#debugWaitForEvent()
                debugLastMergeException = null;
                debugLock = MERGE_THREAD_LOCK_MANAGER.createLock("thread");
            }
            if (reloadData != null) {
                // reload the data if required
                data = reloadData.reload();
            }
            // start the thread
            super.start();
        }
    }

    // the store
    private final HybridStore hybridStore;
    // the files to use
    private final HybridStoreFiles hybridStoreFiles;

    /**
     * create a merge runnable handler
     * @param hybridStore the store to handle
     */
    public MergeRunnable(HybridStore hybridStore) {
        this.hybridStore = hybridStore;
        this.hybridStoreFiles = hybridStore.getHybridStoreFiles();
    }

    /**
     * create a lock to prevent new connection
     *
     * @param alias alias for logs
     * @return the {@link Lock}
     */
    private Lock createConnectionLock(String alias) {
        Lock l = hybridStore.lockToPreventNewConnections.createLock(alias);

        if (MergeRunnableStopPoint.debug) {
            MergeRunnableStopPoint.setLastLock(l);
        }

        return l;
    }
    /**
     * create a lock to prevent new update
     *
     * @param alias alias for logs
     * @return the {@link Lock}
     */
    private Lock createUpdateLock(String alias) {
        Lock l = hybridStore.lockToPreventNewUpdate.createLock(alias);

        if (MergeRunnableStopPoint.debug) {
            MergeRunnableStopPoint.setLastLock(l);
        }

        return l;
    }

    /**
     * only for test purpose, crash if the stopPoint set with {@link #setStopPoint(MergeRunnableStopPoint)} == point
     *
     * @param point the point to crash
     * @throws MergeRunnableStopPoint.MergeRunnableStopException if this point is selected
     */
    private void debugStepPoint(MergeRunnableStopPoint point) {
        if (!MergeRunnableStopPoint.debug)
            return;

        point.debugUnlock();
        point.debugWaitForTestEvent();

        logger.debug("Complete merge runnable step " + point.name());
        if (stopPoint == point) {
            stopPoint = null;
            point.debugThrowStop();
        }

    }

    /**
     * wait all active connection locks
     *
     * @throws InterruptedException
     */
    private void waitForActiveConnections() throws InterruptedException {
        logger.debug("Waiting for connections...");
        hybridStore.locksHoldByConnections.waitForActiveLocks();
        logger.debug("All connections completed.");
    }
    /**
     * wait all active updates locks
     *
     * @throws InterruptedException
     */
    private void waitForActiveUpdates() throws InterruptedException {
        logger.debug("Waiting for updates...");
        hybridStore.locksHoldByUpdates.waitForActiveLocks();
        logger.debug("All updates completed.");
    }

    /**
     * @return a new thread to merge the store
     */
    public MergeThread<?> createThread() {
        return new MergeThread<>(this::step1, false);
    }

    /**
     * @return an optional thread to restart a previous merge (if any)
     */
    public Optional<MergeThread<?>> createRestartThread() {
        // @todo: check previous step with into previousMergeFile, return a thread with the runStep
        int step = getRestartStep();
        logger.debug("Restart step: {}", step);
        switch (step) {
            case 0:
                return Optional.of(new MergeThread<>(this::step1, this::reloadDataFromStep1));
            case 2:
                return Optional.of(new MergeThread<>(this::step2, this::reloadDataFromStep2));
            case 3:
                return Optional.of(new MergeThread<>(this::preloadStep3, this::step3, this::reloadDataFromStep3));
            default:
                return Optional.empty();
        }
    }

    /**
     * write the restart step in the merge file
     *
     * @param step the restart step to write
     * @throws IOException see {@link Files#writeString(Path, CharSequence, OpenOption...)} ioe
     */
    private void markRestartStepCompleted(int step) throws IOException {
        Files.writeString(Paths.get(hybridStoreFiles.getPreviousMergeFile()), String.valueOf(step));
    }

    /**
     * @return the restart step in the merge file, -1 in case of error
     */
    private int getRestartStep() {
        try {
            String text = Files.readString(Paths.get(hybridStoreFiles.getPreviousMergeFile()));
            return Integer.parseInt(text.trim());
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    /**
     * delete the merge file
     *
     * @throws IOException see {@link Files#delete(Path)} ioe
     */
    private void completedMerge() throws IOException {
        Files.delete(Paths.get(hybridStoreFiles.getPreviousMergeFile()));
    }

    /**
     * (debug), sleep for seconds s if seconds != -1
     * @param seconds the number of seconds to sleep
     * @param title the title in the debug logs
     */
    private void sleep(int seconds, String title) {
        if (seconds != -1) {
            try {
                logger.debug("It is sleeping " + title + " " + seconds);
                Thread.sleep(seconds * 1000L);
                logger.debug("Finished sleeping " + title);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * reload previous data from step 1
     * @return previous data from step 2
     */
    private Lock reloadDataFromStep1() {
        return createUpdateLock("step1-lock");
    }

    /**
     * start the merge at step1
     * @param restarting if we are restarting from step 1 or not
     * @param switchLock the return value or {@link #reloadDataFromStep1()}
     * @throws InterruptedException for wait exception
     * @throws IOException for file exception
     */
    private synchronized void step1(boolean restarting, Lock switchLock) throws InterruptedException, IOException {
        logger.info("Start Merge process...");
        markRestartStepCompleted(0);

        debugStepPoint(MergeRunnableStopPoint.STEP1_START);

        logger.debug("Start Step 1");

        // if we aren't restarting, create the lock, otherwise switchLock already contains the update lock
        if (!restarting) {
            switchLock = createUpdateLock("step1-lock");
        }
        // create a lock so that new incoming connections don't do anything
        // wait for all running updates to finish
        waitForActiveUpdates();

        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_BITMAP1);
        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_SELECT1);

        // init the temp deletes while merging... triples that are deleted while merging might be in the newly generated HDT file
        hybridStore.initTempDump(restarting);
        hybridStore.initTempDeleteArray();

        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_BITMAP2);
        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_SELECT2);

        // mark in the store that the merge process started
        hybridStore.setMerging(true);

        sleep(extendsTimeMergeBeginning, "extendsTimeMergeBeginning");
        debugStepPoint(MergeRunnableStopPoint.STEP1_OLD_SLEEP_BEFORE_SWITCH);


        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_SELECT3);

        // switch the store to freeze it
        this.hybridStore.switchStore = !this.hybridStore.switchStore;

        debugStepPoint(MergeRunnableStopPoint.STEP1_TEST_SELECT4);

        // reset the count of triples to 0 after switching the stores
        this.hybridStore.setTriplesCount(0);

        sleep(extendsTimeMergeBeginningAfterSwitch, "extendsTimeMergeBeginningAfterSwitch");
        debugStepPoint(MergeRunnableStopPoint.STEP1_OLD_SLEEP_AFTER_SWITCH);

        // make a copy of the delete array so that the merge thread doesn't interfere with the store data access @todo: a lock is needed here
        if (restarting) {
            // delete previous array in case of restart
            Files.deleteIfExists(Paths.get(hybridStoreFiles.getTripleDeleteCopyArr()));
        }
        Files.copy(Paths.get(hybridStoreFiles.getTripleDeleteArr()),
                Paths.get(hybridStoreFiles.getTripleDeleteCopyArr()));
        // release the lock so that the connections can continue
        switchLock.release();
        debugStepPoint(MergeRunnableStopPoint.STEP1_END);
        logger.debug("Switch-Lock released");
        logger.info("End merge step 1");

        // @todo: set these operations in an atomic way
        // write the switchStore value to disk in case, something crash we can recover
        this.hybridStore.writeWhichStore();
        markRestartStepCompleted(2);
        step2(false, null);
    }

    /**
     * reload previous data from step 2
     * @return previous data of step 2
     */
    private Lock reloadDataFromStep2() {
        // @todo: reload data from step 1
        hybridStore.initTempDump(true);
        hybridStore.initTempDeleteArray();
        hybridStore.setMerging(true);
        return null;
    }

    /**
     * start the merge at step2
     * @param restarting if we are restarting from step 2 or not
     * @param lock the return value or {@link #reloadDataFromStep2()}
     * @throws InterruptedException for wait exception
     * @throws IOException for file exception
     */
    private synchronized void step2(boolean restarting, Lock lock) throws InterruptedException, IOException {
        debugStepPoint(MergeRunnableStopPoint.STEP2_START);
        // diff hdt indexes...
        logger.debug("HDT Diff");
        diffIndexes(hybridStoreFiles.getHDTIndex(), hybridStoreFiles.getTripleDeleteCopyArr());
        logger.debug("Dump all triples from the native store to file");
        RepositoryConnection nativeStoreConnection = hybridStore.getConnectionToFreezedStore();
        writeTempFile(nativeStoreConnection, hybridStoreFiles.getRDFTempOutput());
        nativeStoreConnection.commit();
        nativeStoreConnection.close();
        logger.debug("Create HDT index from dumped file");
        createHDTDump(hybridStoreFiles.getRDFTempOutput(), hybridStoreFiles.getHDTTempOutput());
        // cat the original index and the temp index
        catIndexes(hybridStoreFiles.getHDTNewIndexDiff(), hybridStoreFiles.getHDTTempOutput(), hybridStoreFiles.getHDTNewIndex());
        logger.debug("CAT completed!!!!! " + hybridStoreFiles.getLocationHdt());

        debugStepPoint(MergeRunnableStopPoint.STEP2_END);
        markRestartStepCompleted(3);

        // delete the file after the mark if the shutdown occurs during the deletes
        delete(hybridStoreFiles.getRDFTempOutput());
        delete(hybridStoreFiles.getHDTTempOutput());
        delete(hybridStoreFiles.getTripleDeleteCopyArr());
        delete(hybridStoreFiles.getTripleDeleteArr());

        logger.info("End merge step 2");

        step3(false, null);
    }

    /**
     * return value for {@link #getStep3SubStep()}
     * @see #getStep3SubStep()
     */
    private enum Step3SubStep {
        AFTER_INDEX_V11_RENAME,
        AFTER_INDEX_RENAME,
        AFTER_INDEX_V11_OLD_RENAME,
        AFTER_INDEX_OLD_RENAME,
        AFTER_TRIPLEDEL_TMP_OLD_RENAME,
        BEFORE_ALL
    }

    /**
     * @return the sub step of the {@link #step3(boolean, Lock)} method
     */
    private Step3SubStep getStep3SubStep() {

        boolean existsOldTripleDeleteTempArr = existsOld(hybridStoreFiles.getTripleDeleteTempArr());

        if (!exists(hybridStoreFiles.getHDTNewIndexV11()) && existsOldTripleDeleteTempArr) {
//            after rename(hybridStoreFiles.getHDTNewIndexV11(), hybridStoreFiles.getHDTIndexV11());
            return Step3SubStep.AFTER_INDEX_V11_RENAME;
        }
        if (!exists(hybridStoreFiles.getHDTNewIndex()) && existsOldTripleDeleteTempArr) {
//            after rename(hybridStoreFiles.getHDTNewIndex(), hybridStoreFiles.getHDTIndex());
            return Step3SubStep.AFTER_INDEX_RENAME;
        }
        if (existsOld(hybridStoreFiles.getHDTIndexV11())) {
//            after renameToOld(hybridStoreFiles.getHDTIndexV11());
            return Step3SubStep.AFTER_INDEX_V11_OLD_RENAME;
        }
        if (existsOld(hybridStoreFiles.getHDTIndex())) {
//            after renameToOld(hybridStoreFiles.getHDTIndex());
            return Step3SubStep.AFTER_INDEX_OLD_RENAME;
        }
        if (existsOldTripleDeleteTempArr) {
//            after renameToOld(hybridStoreFiles.getTripleDeleteTempArr());
            return Step3SubStep.AFTER_TRIPLEDEL_TMP_OLD_RENAME;
        }
        return Step3SubStep.BEFORE_ALL;
    }

    /**
     * preload data for step 3 if restarting
     */
    private void preloadStep3() {
        deleteIfExists(hybridStoreFiles.getTripleDeleteArr());

        Step3SubStep step3SubStep = getStep3SubStep();

        logger.debug("Reloading step 3 from sub step {}", step3SubStep.name().toLowerCase());

        switch (step3SubStep) {
            case AFTER_INDEX_V11_RENAME:
                rename(hybridStoreFiles.getHDTIndexV11(), hybridStoreFiles.getHDTNewIndexV11());
            case AFTER_INDEX_RENAME:
                rename(hybridStoreFiles.getHDTIndex(), hybridStoreFiles.getHDTNewIndex());
            case AFTER_INDEX_V11_OLD_RENAME:
                renameFromOld(hybridStoreFiles.getHDTIndexV11());
            case AFTER_INDEX_OLD_RENAME:
                renameFromOld(hybridStoreFiles.getHDTIndex());
            case AFTER_TRIPLEDEL_TMP_OLD_RENAME:
                renameFromOld(hybridStoreFiles.getTripleDeleteTempArr());
            case BEFORE_ALL:
                break;
        }
    }

    /**
     * reload previous data from step 3
     * @return previous data of step 3
     */
    private Lock reloadDataFromStep3() {
        hybridStore.initTempDump(true);
        hybridStore.initTempDeleteArray();
        hybridStore.setMerging(true);

        return createConnectionLock("translate-lock");
    }

    /**
     * start the merge at step3
     * @param restarting if we are restarting from step 3 or not
     * @param lock the return value or {@link #reloadDataFromStep3()}
     * @throws InterruptedException for wait exception
     * @throws IOException for file exception
     */
    private synchronized void step3(boolean restarting, Lock lock) throws InterruptedException, IOException {
        logger.debug("Start Step 3");
        debugStepPoint(MergeRunnableStopPoint.STEP3_START);
        // index the new file

        HDT newHdt = HDTManager.mapIndexedHDT(hybridStoreFiles.getHDTNewIndex(), hybridStore.getHDTSpec());

        // convert all triples added to the merge store to new IDs of the new generated HDT
        logger.debug("ID conversion");
        // create a lock so that new incoming connections don't do anything
        Lock translateLock;
        if (!restarting) {
            translateLock = createConnectionLock("translate-lock");
            // wait for all running updates to finish
            waitForActiveConnections();
        } else {
            translateLock = lock;
        }

        this.hybridStore.resetDeleteArray(newHdt);
        newHdt.close();

        // rename new hdt to old hdt name so that they are replaces
        // BEFORE_ALL
        renameToOld(hybridStoreFiles.getTripleDeleteTempArr());
        // AFTER_TRIPLEDEL_TMP_OLD_RENAME
        renameToOld(hybridStoreFiles.getHDTIndex());
        debugStepPoint(MergeRunnableStopPoint.STEP3_FILES_MID1);
        // AFTER_INDEX_OLD_RENAME
        renameToOld(hybridStoreFiles.getHDTIndexV11());
        // AFTER_INDEX_V11_OLD_RENAME
        rename(hybridStoreFiles.getHDTNewIndex(), hybridStoreFiles.getHDTIndex());
        debugStepPoint(MergeRunnableStopPoint.STEP3_FILES_MID2);
        // AFTER_INDEX_RENAME
        rename(hybridStoreFiles.getHDTNewIndexV11(), hybridStoreFiles.getHDTIndexV11());
        // AFTER_INDEX_V11_RENAME

        HDT tempHdt = HDTManager.mapIndexedHDT(hybridStoreFiles.getHDTIndex(), this.hybridStore.getHDTSpec());
        convertOldToNew(this.hybridStore.getHdt(), tempHdt);
        this.hybridStore.resetHDT(tempHdt);

        // mark the triples as deleted from the temp file stored while merge
        this.hybridStore.markDeletedTempTriples();
        logger.debug("Releasing lock for ID conversion ....");
        translateLock.release();
        logger.debug("Translate-Lock released");
        logger.debug("Lock released");

        debugStepPoint(MergeRunnableStopPoint.STEP3_END);
        completedMerge();
        // we've deleted the rename marker, we can delete the old HDT
        deleteOld(hybridStoreFiles.getTripleDeleteTempArr());
        deleteOld(hybridStoreFiles.getHDTIndex());
        deleteOld(hybridStoreFiles.getHDTIndexV11());

        debugStepPoint(MergeRunnableStopPoint.MERGE_END);

        this.hybridStore.setMerging(false);
        this.hybridStore.isMergeTriggered = false;

        debugStepPoint(MergeRunnableStopPoint.MERGE_END_OLD_SLEEP);

        logger.info("Merge finished");
    }

    private void diffIndexes(String hdtInput1, String bitArray) {
        String hdtOutput = hybridStoreFiles.getHDTNewIndexDiff();
        try {
            File hdtOutputFile = new File(hdtOutput);
            File theDir = new File(hdtOutputFile.getAbsolutePath() + "_tmp");
            theDir.mkdirs();
            String location = theDir.getAbsolutePath() + "/";
            // @todo: should we not use the already mapped HDT file instead of remapping
            HDT hdt = HDTManager.diffHDTBit(location, hdtInput1, bitArray, this.hybridStore.getHDTSpec(), null);
            hdt.saveToHDT(hdtOutput, null);

            Files.delete(Paths.get(location + "dictionary"));
            FileUtils.deleteDirectory(theDir.getAbsoluteFile());
            hdt.close();
        } catch (Exception e) {
            hybridStore.setMerging(false);
            e.printStackTrace();
        }
    }

    private void catIndexes(String hdtInput1, String hdtInput2, String hdtOutput) throws IOException {
        HDT hdt = null;
        try {
            File file = new File(hdtOutput);
            File theDir = new File(file.getAbsolutePath() + "_tmp");
            theDir.mkdirs();
            String location = theDir.getAbsolutePath() + "/";
            logger.info(location);
            logger.info(hdtInput1);
            logger.info(hdtInput2);
            // @todo: should we not use the already mapped HDT file instead of remapping
            hdt = HDTManager.catHDT(location, hdtInput1, hdtInput2, this.hybridStore.getHDTSpec(), null);

            StopWatch sw = new StopWatch();
            hdt.saveToHDT(hdtOutput, null);
            hdt.close();
            logger.info("HDT saved to file in: " + sw.stopAndShow());
            Files.delete(Paths.get(location + "dictionary"));
            Files.delete(Paths.get(location + "triples"));
            theDir.delete();
            Files.deleteIfExists(Paths.get(hdtInput1));
            Files.deleteIfExists(Paths.get(hdtInput1 + HDTVersion.get_index_suffix("-")));
        } catch (Exception e) {
            e.printStackTrace();
            hybridStore.setMerging(false);
        } finally {
            if (hdt != null)
                hdt.close();
        }
    }

    private void createHDTDump(String rdfInput, String hdtOutput) {
        String baseURI = "file://" + rdfInput;
        try {
            StopWatch sw = new StopWatch();
            HDT hdt = HDTManager.generateHDT(new File(rdfInput).getAbsolutePath(), baseURI, RDFNotation.NTRIPLES, this.hybridStore.getHDTSpec(), null);
            logger.info("File converted in: " + sw.stopAndShow());
            hdt.saveToHDT(hdtOutput, null);
            logger.info("HDT saved to file in: " + sw.stopAndShow());
        } catch (IOException | ParserException e) {
            e.printStackTrace();
            hybridStore.setMerging(false);
        }
    }

    private void writeTempFile(RepositoryConnection connection, String file) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            RepositoryResult<Statement> repositoryResult =
                    connection.getStatements(null, null, null, false);
            writer.startRDF();
            logger.debug("Content dumped file");
            while (repositoryResult.hasNext()) {
                Statement stm = repositoryResult.next();

                Resource newSubjIRI = this.hybridStore.getHdtConverter().rdf4jToHdtIDsubject(stm.getSubject());
                newSubjIRI = this.hybridStore.getHdtConverter().subjectHdtResourceToResource(newSubjIRI);

                IRI newPredIRI = this.hybridStore.getHdtConverter().rdf4jToHdtIDpredicate(stm.getPredicate());
                newPredIRI = this.hybridStore.getHdtConverter().predicateHdtResourceToResource(newPredIRI);

                Value newObjIRI = this.hybridStore.getHdtConverter().rdf4jToHdtIDobject(stm.getObject());
                newObjIRI = this.hybridStore.getHdtConverter().objectHdtResourceToResource(newObjIRI);

                Statement stmConverted = this.hybridStore.getValueFactory().createStatement(
                        newSubjIRI,
                        newPredIRI,
                        newObjIRI
                );
                logger.debug("  {} {} {}", stmConverted.getSubject(), stmConverted.getPredicate(), stmConverted.getObject());
                writer.handleStatement(stmConverted);
            }
            writer.endRDF();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            hybridStore.setMerging(false);
        }
    }

    private void convertOldToNew(HDT oldHDT, HDT newHDT) {
        logger.info("Started converting IDs in the merge store");
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            RepositoryConnection connectionChanging = this.hybridStore.getConnectionToChangingStore(); // B
            RepositoryConnection connectionFreezed = this.hybridStore.getConnectionToFreezedStore(); // A
            connectionFreezed.clear();
            connectionFreezed.begin();
            RepositoryResult<Statement> statements = connectionChanging.getStatements(null, null, null);
            int count = 0;
            for (Statement s : statements) {
                count++;
                // get the string
                // convert the string using the new dictionary


                // get the old IRIs with old IDs
                HDTConverter iriConverter = new HDTConverter(this.hybridStore);
                Resource oldSubject = iriConverter.rdf4jToHdtIDsubject(s.getSubject());
                IRI oldPredicate = iriConverter.rdf4jToHdtIDpredicate(s.getPredicate());
                Value oldObject = iriConverter.rdf4jToHdtIDobject(s.getObject());

                // if the old string cannot be converted than we can keep the same
                Resource newSubjIRI = oldSubject;
                long id = newHDT.getDictionary().stringToId(oldSubject.toString(), TripleComponentRole.SUBJECT);
                if (id != -1) {
                    newSubjIRI = iriConverter.subjectIdToIRI(id);
                }

                IRI newPredIRI = oldPredicate;
                id = newHDT.getDictionary().stringToId(oldPredicate.toString(), TripleComponentRole.PREDICATE);
                if (id != -1) {
                    newPredIRI = iriConverter.predicateIdToIRI(id);
                }
                Value newObjIRI = oldObject;
                id = newHDT.getDictionary().stringToId(oldObject.toString(), TripleComponentRole.OBJECT);
                if (id != -1) {
                    newObjIRI = iriConverter.objectIdToIRI(id);
                }
                logger.debug("old:[{} {} {}]", oldSubject, oldPredicate, oldObject);
                logger.debug("new:[{} {} {}]", newSubjIRI, newPredIRI, newObjIRI);
                connectionFreezed.add(newSubjIRI, newPredIRI, newObjIRI);
//                alternative, i.e. make inplace replacements
//                connectionChanging.remove(s.getSubject(), s.getPredicate(), s.getObject());
//                connectionChanging.add(newSubjIRI, newPredIRI, newObjIRI);

                if (count % 10000 == 0) {
                    logger.debug("Converted {}", count);
                }

            }
            connectionFreezed.commit();
            connectionChanging.clear();
            connectionChanging.close();
            connectionFreezed.close();
            // @todo: why?
            this.hybridStore.switchStore = !this.hybridStore.switchStore;

            stopwatch.stop(); // optional
            logger.info("Time elapsed for conversion: " + stopwatch);

            // initialize bitmaps again with the new dictionary
            Files.deleteIfExists(Paths.get(hybridStoreFiles.getHDTBitX()));
            Files.deleteIfExists(Paths.get(hybridStoreFiles.getHDTBitY()));
            Files.deleteIfExists(Paths.get(hybridStoreFiles.getHDTBitZ()));
            stopwatch = Stopwatch.createStarted();
            logger.info("Time elapsed to initialize native store dictionary: " + stopwatch);
        } catch (Exception e) {
            logger.error("Something went wrong during conversion of IDs in merge phase: ");
            e.printStackTrace();
            hybridStore.setMerging(false);
        }
    }

}