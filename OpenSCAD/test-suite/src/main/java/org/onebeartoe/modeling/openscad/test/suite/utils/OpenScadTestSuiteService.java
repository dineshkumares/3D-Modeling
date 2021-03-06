
package org.onebeartoe.modeling.openscad.test.suite.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.onebeartoe.modeling.openscad.test.suite.OpenScadCameraDirections;
import org.onebeartoe.modeling.openscad.test.suite.OpenScadTestSuite;
import org.onebeartoe.modeling.openscad.test.suite.RunProfile;
import org.onebeartoe.system.CommandResults;
import org.onebeartoe.system.command.SystemCommand;
import org.onebeartoe.system.command.imagemagick.Compare;

/**
 * This class provides methods used in the OpenSCAD test suite.
 * 
 * @author Roberto Marquez <https://www.youtube.com/user/onebeartoe>
 */
public class OpenScadTestSuiteService 
{
    private Logger logger;
    
    private PngGenerator pngGenerator;    

    public OpenScadTestSuiteService()
    {
        String name = getClass().getName(); 
        logger = Logger.getLogger(name);
        
        pngGenerator = new PngGenerator();        
    }
    
    /**
     * It seems like this method should be refactored.
     * 
     */
    public void serviceRequest(RunProfile runProfile) throws Exception
    {
        OpenScadTestSuite.RunMode mode;

// the first hachifufoo
        if(runProfile.generateBaselines)
        {
            mode = OpenScadTestSuite.RunMode.GENERATE_BASELINES;
        }
        else if(runProfile.deleteProposedBaseLines)
        {
            mode = OpenScadTestSuite.RunMode.DELETE_PROPOSED_BASELINES;
        }
        else if(runProfile.diffOnly)
        {                
            mode = OpenScadTestSuite.RunMode.RUN_TEST_SUITE;
        }
        else
        {
            mode = OpenScadTestSuite.RunMode.RUN_TEST_SUITE;
        }

        OpenScadFileFinder openScadFinder = new OpenScadFileFinder();
        Path inpath = FileSystems.getDefault().getPath(runProfile.path);

        try
        {
            runProfile.openscadPaths = openScadFinder.getFiles(inpath);

            if(mode == OpenScadTestSuite.RunMode.GENERATE_BASELINES)
            {
                generateBaselines(runProfile);
            }
            else if( mode == OpenScadTestSuite.RunMode.DELETE_PROPOSED_BASELINES)
            {
                deleteProposedBaselines(inpath);
            }
            else
            {
                // the mode is 'run test suite'
                boolean passed = runTestSuite(runProfile);

                if(!passed)
                {
                    throw new Exception("Did not pass");
                }
            }                
        }
        catch(Exception nsfe)
        {
            File pwd = new File(".");
            System.err.println("pwd: " + pwd.getAbsolutePath() );

            nsfe.printStackTrace();

            throw new Exception(nsfe);
        }        
    }    

    private void generateBaselines(RunProfile runProfile) throws IOException, InterruptedException
    {
        System.out.println("test suite generating baselines, count: " + runProfile.openscadPaths.size());

        // don't overwrite any existing baseline images
        boolean forcePngGeneration = false;

//TODO: Move forcePngGeneration into the RunProfile class.
        pngGenerator.generatePngs(forcePngGeneration, runProfile);
    }

    /**
     * 
     * @return The count of proposed baseline images generated.
     * @throws IOException
     * @throws InterruptedException 
     */
    public int generateProposedBaselines(RunProfile runProfile) throws IOException, InterruptedException
    {
        // create the proposed baseline images every time the test suite is run
        boolean forcePngGeneration = true;
        
        boolean success = pngGenerator.generatePngs(forcePngGeneration,
                                                    runProfile);
        int count;
        
        if(success)
        {
            count = runProfile.openscadPaths.size();
        }
        else
        {
//TODO: this should use a successCount returned by pngGenerator.generatePngs()
            count = -1;
            
            System.err.println();
            System.err.println("ERROR detected: Some PNGs were not generated.");
        }
        
        return count;
    }
    
    /**
     * 
     * @param runProfile
     * @return This method retuns a list of files that had an error with 
     *         baseline and proposed baseline comparison.
     */
    public ImageComparisonResult compareImages(RunProfile runProfile)
    {
        System.out.println();
        System.out.println("Comparing baseline images to the proposed baseline images...");

        boolean passed = true;
        
        ImageComparisonResult results = compareImages(runProfile.openscadPaths);

        // verify Check if the diffs were successful
        if(results.errorFiles.size() == 0 && !results.exceptionThrown)
        {
            System.out.println();
            System.out.println("No test suite errors were detected.");
            System.out.println();
            System.out.println("Thanks for using the onebeartoe test suite for OpenSCAD libraries.");
        }
        else
        {
            // whoa, whoa
            passed = false;

            System.out.println( System.lineSeparator() );
            
            if(results.exceptionThrown)
            {
                System.out.println("Image comparison exceptions occured.");
            }
            else
            {
                System.out.println("The test suite detected " + results.errorFiles.size() + " errors with the baseline and proposed baseline PNG images.");
            }
            
            System.out.println();

            System.out.println("See the compare commands above.");        	
        }

        return results;
    }
    
    /**
     * 
     * @return a list of any files that did not pass the diff test
     */
    private ImageComparisonResult compareImages(List<Path> openscadPaths)
    {
//	List<String> errorFiles = new ArrayList();
	
        Class c = Collection.class;
        
        ImageComparisonResult comparisonResults = new ImageComparisonResult();

//        boolean exceptionThrown;
        
	Stream.of( OpenScadCameraDirections.values() )
                .parallel()
              .forEach((OpenScadCameraDirections direction) -> 
        {
            openscadPaths.parallelStream().forEach((Path p) ->
            {
                boolean forceGeneration = false;
                String baseline = DataSetValidator.baselineNameFor(p, forceGeneration, direction);
                
                forceGeneration = true;
                String proposedBaseline = DataSetValidator.baselineNameFor(p, forceGeneration, direction);
                
                try
                {
                    SystemCommand diffCommand = new Compare(baseline, proposedBaseline);
                    CommandResults results = diffCommand.execute();
                    
                    // check if the exit code is 0 for success
                    if(results.exitCode == 0)
                    {
                        comparisonResults.successFiles.add(baseline);
                    }
                    else
                    {
                        comparisonResults.errorFiles.add(baseline);
                        
                        System.out.println( results.processedStdErr.trim() );
                        System.out.print( results.processedStdOut.trim() );
                    }
                }
                catch (Exception e)
                {
                    comparisonResults.exceptionThrown = true;
                    String message = "An error occured while executing a diff command.";
                    logger.log(Level.SEVERE, message, e);
                }
            });
        });
	
	return comparisonResults;
    }
    
    private void deleteProposedBaselines(Path inpath) throws IOException
    {
        // find all the proposed baseline images
        List<File> proposedBaselines = findProposedBaselines(inpath);
        
        // and delete them
        proposedBaselines.forEach( pb -> 
        {
            System.out.println("deleteing: " + pb.getPath() );
            
            pb.delete();
        });
    }
    
    private List<File> findProposedBaselines(Path inpath) throws IOException
    {
        ProposedBaselineFinder finder = new ProposedBaselineFinder();
        
        List<File> proposedBaselines = finder.find(inpath);
        
        return proposedBaselines;
    }

    public void printOpernScadVersion(RunProfile runProfile)
    {
// TODO: p        
//       rint the version of poesn scdd
    }
    
    public void printValidationResults(List<String> missingBaselineFiles)
    {
	System.out.println();
	
        if (missingBaselineFiles.isEmpty())
        {
            String pwd = (new File(".")).getAbsolutePath();
            System.out.println("The current working directory is: " + pwd);
            
            System.out.println("All input files are present.");
        }
        else
        {
            int count = missingBaselineFiles.size();
            System.err.println("Some " + count + " test suite input files are not present.");
            System.err.println();
            System.err.println("Try running '--" + OpenScadTestSuite.GENERATE_BASELILNES +"' to generate the missing input files.");
        }
    }
    
    private boolean runTestSuite(RunProfile runProfile) throws Exception
    {
        boolean passed = true;
        
        System.out.println("Welcome to the onebeartoe OpenSCAD test suite!");

        DataSetValidator inputValidator = new DataSetValidator();
        List<String> missingPngs = inputValidator.validate(runProfile.openscadPaths);
        printValidationResults(missingPngs);

        if (!missingPngs.isEmpty())
        {
            passed = false;
            
            System.err.println();
            System.err.println("The test suite will not continue with missing baseline PNG images.");
        }
        else
        {
            boolean proposedBaselineError = false;
            
            boolean skipProposedBaselineGeneration = runProfile.diffOnly;
            
            if(skipProposedBaselineGeneration)
            {
                System.out.println("Generation of the proposed baselines PNGs is being skipped.");
            }
            else
            {
                System.out.println();
                System.out.println("The test suite is now generating proposed baseline images for each .oscad file.");
                System.out.println();
                
// TODO:                 
//                printOpernScadVersion(RunProfile runProfile);
                
                int count = generateProposedBaselines(runProfile);
                
                // check if the count is less than 0
                if(count < 0)
                {
                    proposedBaselineError = true;
                }
                else
                {
                    System.out.println();
                    System.out.println("Proposed baselines were generated for "
                            + count
                            + " OpenSCAD files.");
                }
            }

            if(proposedBaselineError)
            {
                passed = false;
                
                // halt the test execution and let the user know about errors
                String message = "Not all proposed baseline PNGs were generated.  ";
                message += "Please correct the errors to allow the test suite to continue";
                
                System.err.println(message);
            }
            else
            {
                compareImages(runProfile);
            }
        }
        
        return passed;
    }
}
