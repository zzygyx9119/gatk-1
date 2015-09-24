package org.broadinstitute.hellbender.tools.exome;

import org.broadinstitute.hellbender.CommandLineProgramTest;
import org.broadinstitute.hellbender.utils.segmenter.RCBSSegmenter;
import org.broadinstitute.hellbender.utils.segmenter.SegmenterTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class PerformCBSSegmentationIntegrationTest extends CommandLineProgramTest{

    @DataProvider(name="inputFileData")
    public Object[][] inputFileData() {
        return new Object[][] {
                new Object[] { new File("src/test/resources/segmenter/input/HCC1143_reduced.tsv"), new File("src/test/resources/segmenter/output/HCC1143_reduced_result.seg"), createTempFile("recapseg.HCC1143", ".seg"), "HCC1143"},
                new Object[] { new File("src/test/resources/segmenter/input/HCC1143_short.tsv"), new File("src/test/resources/segmenter/output/HCC1143_short_result.seg"), createTempFile("recapseg.HCC1143", ".seg"), "HCC1143"},
                new Object[] { new File("src/test/resources/segmenter/input/Simple.tsv"), new File("src/test/resources/segmenter/output/Simple_result.seg"), createTempFile("recapseg.HCC1143", ".seg"), "Simple"},
        };
    }

    @Test(dataProvider = "inputFileData")
    public void testHCC1143ReducedCommandLine(final File INPUT_FILE, final File EXPECTED, final File output, String sampleName) throws IOException {
        RCBSSegmenter.writeSegmentFile(sampleName, INPUT_FILE.getAbsolutePath(), output.getAbsolutePath());
        final String[] arguments = {
                "-" + PerformCBSSegmentation.SAMPLE_NAME_SHORT_NAME, sampleName,
                "-" + PerformCBSSegmentation.TARGETS_FILE_SHORT_NAME, INPUT_FILE.getAbsolutePath(),
                "-" + PerformCBSSegmentation.SEGMENT_FILE_SHORT_NAME, output.getAbsolutePath(),
        };
        runCommandLine(arguments);
        SegmenterTest.assertEqualSegments(output, EXPECTED);
    }
}