package org.broadinstitute.hellbender.engine.spark.datasources;

import com.google.api.services.genomics.model.Read;
import com.google.cloud.genomics.utils.ReadUtils;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.broadinstitute.hellbender.exceptions.GATKException;
import org.broadinstitute.hellbender.utils.IntervalUtils;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.read.GoogleGenomicsReadToGATKReadAdapter;
import org.seqdoop.hadoop_bam.AnySAMInputFormat;
import org.seqdoop.hadoop_bam.SAMRecordWritable;
import org.seqdoop.hadoop_bam.util.SAMHeaderReader;

import java.io.IOException;
import java.util.List;

/** Loads the reads from disk either serially (using samReaderFactory) or in parallel using Hadoop-BAM.
 * The parallel code is a modified version of the example writing code from Hadoop-BAM.
 */
public class ReadsSparkSource {
    private final JavaSparkContext ctx;
    public ReadsSparkSource(JavaSparkContext ctx) {
        this.ctx = ctx;
    }


    /**
     * Loads Reads using Hadoop-BAM. For local files, bam must have the fully-qualified path,
     * i.e., file:///path/to/bam.bam.
     * @param bam file to load
     * @param intervals intervals of reads to include.
     * @return RDD of (Google Read-backed) GATKReads from the file.
     */
    public JavaRDD<GATKRead> getParallelReads(final String bam, final List<SimpleInterval> intervals) {
        Configuration conf = new Configuration();
        conf.set("mapred.max.split.size", "2097152");

        JavaPairRDD<LongWritable, SAMRecordWritable> rdd2 = ctx.newAPIHadoopFile(
                bam, AnySAMInputFormat.class, LongWritable.class, SAMRecordWritable.class,
                conf);

        return rdd2.map(v1 -> {
            SAMRecord sam = v1._2().get();
            if (samRecordOverlaps(sam, intervals)) {
                try {
                    // TODO: Try using the SAMRecord without the header (#875)
                    Read read = ReadUtils.makeRead(sam);
                    if (read == null) {
                        throw new GATKException("null read, initial sam: " + sam);
                    }
                    return GoogleGenomicsReadToGATKReadAdapter.sparkReadAdapter(read);
                } catch (SAMException e) {
                    // Do nothing.
                }
            }
            return null;

        }).filter(v1 -> v1 != null);
    }

    /**
     * Loads Reads using Hadoop-BAM. For local files, bam must have the fully-qualified path,
     * i.e., file:///path/to/bam.bam. This excludes unmapped reads.
     * @param bam file to load
     * @return RDD of (SAMRecord-backed) GATKReads from the file.
     */
    public JavaRDD<GATKRead> getParallelReads(final String bam) {
        final SAMFileHeader readsHeader = getHeader(ctx, bam);
        List<SimpleInterval> intervals = IntervalUtils.getAllIntervalsForReference(readsHeader.getSequenceDictionary());
        return getParallelReads(bam, intervals);
    }

    /**
     * Loads the header using Hadoop-BAM.
     * @param filePath path to the bam.
     * @return the header for the bam.
     */
    public static SAMFileHeader getHeader(final JavaSparkContext ctx, final String filePath) {
        final SAMFileHeader samFileHeader;
        try {
             samFileHeader = SAMHeaderReader.readSAMHeaderFrom(new Path(filePath), ctx.hadoopConfiguration());
        } catch (IOException e) {
            throw new GATKException("unable to loader header: " + e);
        }
        return samFileHeader;
    }

    /**
     * Tests if a given SAMRecord overlaps any interval in a collection.
     */
    //TODO: remove this method when https://github.com/broadinstitute/hellbender/issues/559 is fixed
    private static boolean samRecordOverlaps(final SAMRecord record, final List<SimpleInterval> intervals ) {
        if (intervals == null || intervals.isEmpty()) {
            return true;
        }
        for (SimpleInterval interval : intervals) {
            if (interval.overlaps(record)) {
                return true;
            }
        }
        return false;
    }
}