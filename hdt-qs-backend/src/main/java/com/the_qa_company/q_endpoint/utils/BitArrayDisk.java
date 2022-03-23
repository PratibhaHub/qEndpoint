package com.the_qa_company.q_endpoint.utils;

import org.eclipse.rdf4j.common.io.NioFile;
import org.rdfhdt.hdt.compact.bitmap.ModifiableBitmap;
import org.rdfhdt.hdt.listener.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Implementation of {@link org.rdfhdt.hdt.compact.bitmap.ModifiableBitmap} write on disk
 */
public class BitArrayDisk implements ModifiableBitmap {

    protected final static int LOGW = 6;
    protected final static int W = 64;

    /**
     * compute the number of the highest bit of a value
     * @param value the value
     * @return the number of the highest bit of value
     */
    static int log2(long value) {
        if (value == 0) {
            return 0; // Wrong, but it's private
        }
        long v = value;
        int log = 0;

        while (v != 0) {
            v >>>= 1;
            log++;
        }

        return log;
    }
    protected long numbits;
    protected long allBits;
    protected long[] words;

    private static final int BLOCKS_PER_SUPER = 4;

    // Variables
    private long pop;
    private long[] superBlocksLong;
    private int[] superBlocksInt;
    private byte[] blocks;
    private boolean indexUpToDate;

    NioFile output;

    // only for testing we don't necessarily need to store the array on disk
    private boolean inMemory = false;


    /**
     * create a on disk bit array
     * @param nbits the number of bits to allocate
     * @param location the array location
     */
    public BitArrayDisk(long nbits, String location) {
        this(nbits, new File(location));
    }

    /**
     * create a in memory bit array, can be switch to a on disk bit array with {@link #changeToInDisk(java.io.File)}
     * @param nbits the number of bits to allocate
     */
    public BitArrayDisk(long nbits) {
        this.numbits = 0;
        this.inMemory = true;
        initWordsArray(nbits);
    }

    /**
     * create a on disk bit array
     * @param nbits the number of bits to allocate
     * @param file the array location
     */
    public BitArrayDisk(long nbits, File file) {
        this.numbits = 0;
        try {
            this.output = new NioFile(file);
            initWordsArray(nbits);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * convert this {@link BitArrayDisk} memory instance to a file instance, only works with
     * instance created with {@link BitArrayDisk#BitArrayDisk(long)}.
     * @param file the file to use
     */
    public void changeToInDisk(File file) {
        assert inMemory: "the BitArray should be in memory";
        inMemory = false;
        try {
            this.output = new NioFile(file);
            writeBits();
            for (int offset = 0; offset < words.length; offset++) {
                output.writeLong(words[offset], 8L * (offset + 1));
            }
            output.force(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * write inside the output file the number of words
     * @throws IOException if the write can be done
     */
    private void writeBits() throws IOException {
        // write the length of the array in the beginning
        int nwords = (int) numWords(allBits);
        this.output.writeLong(nwords, 0);
    }

    private void initWordsArray(long nbits) {
        allBits = nbits;
        try {
            if (!inMemory) {
                if (output.size() == 0) { // file empty
                    int nwords = (int) numWords(allBits);
                    this.words = new long[nwords];
                    writeBits();
                } else {
                    // read the length of the array from the beginning
                    long length = this.output.readLong(0);
                    this.words = new long[(int) length];

                    int lastNonZero = -1;
                    // read previous values
                    for (int i = 0; i < length; i++) {
                        long v = this.output.readLong((i + 1) * 8L);
                        if (v != 0) {
                            this.words[i] = v;
                            lastNonZero = i;
                        }
                    }
                    // recompute numbits if we have at least one bit
                    if (lastNonZero != -1)
                        numbits = 8L * lastNonZero + log2(words[lastNonZero]);
                }
            } else {
                int nwords = (int) numWords(nbits);
                this.words = new long[nwords];
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long numWords(long numbits) {
        return ((numbits - 1) >>> LOGW) + 1;
    }

    protected static int wordIndex(long bitIndex) {
        return (int) (bitIndex >>> LOGW);
    }

    @Override
    public boolean access(long bitIndex) {
        if (bitIndex < 0)
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

        int wordIndex = wordIndex(bitIndex);
        if (wordIndex >= words.length) {
            return false;
        }

        return (words[wordIndex] & (1L << bitIndex)) != 0;
    }

    protected final void ensureSize(int wordsRequired) {
        if (words.length < wordsRequired) {
            long[] newWords = new long[Math.max(words.length * 2, wordsRequired)];
            System.arraycopy(words, 0, newWords, 0, Math.min(words.length, newWords.length));
            words = newWords;
        }
    }

    @Override
    public void set(long bitIndex, boolean value) {
        indexUpToDate = false;
        if (bitIndex < 0)
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

        int wordIndex = wordIndex(bitIndex);
        ensureSize(wordIndex + 1);

        if (value) {
            words[wordIndex] |= (1L << bitIndex);
        } else {
            words[wordIndex] &= ~(1L << bitIndex);
        }

        this.numbits = Math.max(this.numbits, bitIndex + 1);
        if (!inMemory)
            writeToDisk(words[wordIndex], wordIndex);
    }

    @Override
    public void append(boolean value) {
        set(numbits, value);
    }

    private void writeToDisk(long l, int wordIndex) {
        try {
            output.writeLong(l, (wordIndex + 1) * 8L); // +1 reserved for the length of the array
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void trimToSize() {
        int wordNum = (int) numWords(numbits);
        if (wordNum != words.length) {
            words = Arrays.copyOf(words, wordNum);
        }
    }

    public void updateIndex() {
        trimToSize();
        if (numbits > Integer.MAX_VALUE) {
            superBlocksLong = new long[1 + (words.length - 1) / BLOCKS_PER_SUPER];
        } else {
            superBlocksInt = new int[1 + (words.length - 1) / BLOCKS_PER_SUPER];
        }
        blocks = new byte[words.length];

        long countBlock = 0, countSuperBlock = 0;
        int blockIndex = 0, superBlockIndex = 0;

        while (blockIndex < words.length) {
            if ((blockIndex % BLOCKS_PER_SUPER) == 0) {
                countSuperBlock += countBlock;
                if (superBlocksLong != null) {
                    if (superBlockIndex < superBlocksLong.length) {
                        superBlocksLong[superBlockIndex++] = countSuperBlock;
                    }
                } else {
                    if (superBlockIndex < superBlocksInt.length) {
                        superBlocksInt[superBlockIndex++] = (int) countSuperBlock;
                    }
                }
                countBlock = 0;
            }
            blocks[blockIndex] = (byte) countBlock;
            countBlock += Long.bitCount(words[blockIndex]);
            blockIndex++;
        }
        pop = countSuperBlock + countBlock;
        indexUpToDate = true;
    }

    @Override
    public long rank1(long pos) {
        if (pos < 0) {
            return 0;
        }
        if (!indexUpToDate) {
            updateIndex();
        }
        if (pos >= numbits) {
            return pop;
        }

        long superBlockIndex = pos / (BLOCKS_PER_SUPER * W);
        long superBlockRank;
        if (superBlocksLong != null) {
            superBlockRank = superBlocksLong[(int) superBlockIndex];
        } else {
            superBlockRank = superBlocksInt[(int) superBlockIndex];
        }

        long blockIndex = pos / W;
        long blockRank = 0xFF & blocks[(int) blockIndex];

        long chunkIndex = W - 1 - pos % W;
        long block = words[(int) blockIndex] << chunkIndex;
        long chunkRank = Long.bitCount(block);

        return superBlockRank + blockRank + chunkRank;
    }

    @Override
    public long rank0(long pos) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long selectPrev1(long start) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long selectNext1(long start) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long select0(long n) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long select1(long n) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long countOnes() {
        return rank1(numbits);
    }

    @Override
    public long countZeros() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getSizeBytes() {
        return words.length * 8L;
    }

    @Override
    public void save(OutputStream output, ProgressListener listener) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void load(InputStream input, ProgressListener listener) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getType() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getNumBits() {
        return numbits;
    }

    public int getNumWords() {
        return this.words.length;
    }


    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (long i = 0; i < numbits; i++) {
            str.append(access(i) ? '1' : '0');
        }
        return str.toString();
    }

    public String toString(boolean allBits) {
        StringBuilder str = new StringBuilder();
        long end;

        if (allBits) {
            end = this.allBits;
        } else {
            end = numbits;
        }

        for (long i = 0; i < end; i++) {
            str.append(access(i) ? '1' : '0');
        }
        return str.toString();
    }

    public void close() {
        try {
            this.output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void force(boolean bool) {
        if (inMemory)
            return;
        try {
            this.output.force(bool);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String printInfo() {
        return "numWords:" + getNumWords() +
                ", numbits: " + getNumBits() +
                ", ones: " + countOnes() +
                (inMemory ? ", inMemory: true" : "\nfile: " + output.getFile().getAbsolutePath()) +
                (allBits <= 20 ? "\nbits: " + toString(true) : "");
    }
}
