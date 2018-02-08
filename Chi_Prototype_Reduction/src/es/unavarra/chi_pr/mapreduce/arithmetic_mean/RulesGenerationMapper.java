/*
 * Copyright (C) 2015 Mikel Elkano Ilintxeta
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.unavarra.chi_pr.mapreduce.arithmetic_mean;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import es.unavarra.chi_pr.core.FuzzyRule;
import es.unavarra.chi_pr.core.Mediator;
import es.unavarra.chi_pr.core.NominalVariable;
import es.unavarra.chi_pr.utils.ByteArrayWritable;
import es.unavarra.chi_pr.utils.PartialSum;
import es.unavarra.chi_pr.utils.PartialSumsPairsWritable;

/**
 * Mapper class that generates a new rule for each map
 * @author Mikel Elkano Ilintxeta
 * @version 1.0
 */
public class RulesGenerationMapper extends Mapper<Text, Text, ByteArrayWritable, PartialSumsPairsWritable>{
    
	private String[] exampleStr;
	private long[] numExamples;
	private PartialSum[][] partialSums;
	private long[] countCatValues;
	private NominalVariable nomVar;
	private byte[] labels;
	private byte[] antecedents;
	private int i, j, k;
	
	private long startMs, endMs;
	
	@Override
	protected void cleanup (Context context) throws IOException, InterruptedException{
		
		// Write execution time
		endMs = System.currentTimeMillis();
		long mapperID = context.getTaskAttemptID().getTaskID().getId();
		try {
        	FileSystem fs = FileSystem.get(Mediator.getConfiguration());
        	Path file = new Path(Mediator.getHDFSLocation()+Mediator.getOutputPath()+Mediator.TIME_STATS_DIR+"/mapper"+mapperID+".txt");
        	OutputStream os = fs.create(file);
        	BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
        	bw.write("Execution time (seconds): "+((endMs-startMs)/1000));
        	bw.close();
        	os.close();
        }
        catch(Exception e){
        	System.err.println("\nMAP PHASE: ERROR WRITING EXECUTION TIME");
			e.printStackTrace();
        }
		
	}
	
	@Override
    public void map(Text key, Text value, Context context) throws IOException, InterruptedException {

		// Read example
        StringTokenizer st = new StringTokenizer(key.toString()+value.toString(), ", ");
        for (j = 0; j < Mediator.getNumInputVariables() + 1; j++)
            exampleStr[j] = st.nextToken();
		
        // Generate fuzzy rule
        labels = FuzzyRule.getRuleFromExample(exampleStr);
        antecedents = new byte[labels.length-1];
        for (j = 0; j < antecedents.length; j++)
        	antecedents[j] = labels[j];
        
        // Restart the counter of number of examples
    	for (i = 0; i < numExamples.length; i++)
			numExamples[i] = 0;
    	numExamples[labels[labels.length-1]] = 1;
    	
    	// Store input values
    	for (j = 0; j < Mediator.getNumInputVariables(); j++){
    		if (Mediator.getInputVariables()[j] instanceof NominalVariable){
    			nomVar = (NominalVariable)Mediator.getInputVariables()[j];
    			countCatValues = new long[nomVar.getNominalValues().length];
    			for (k = 0; k < countCatValues.length; k++)
    				countCatValues[k] = 0;
    			countCatValues[nomVar.getLabelIndex(exampleStr[j])] = 1;
    			partialSums[labels[labels.length-1]][j] = new PartialSum(countCatValues);
    		}
    		else
    			partialSums[labels[labels.length-1]][j] = new PartialSum(Double.parseDouble(exampleStr[j]));
    	}
        
        /*
    	 * Key: Antecedents of the rule
    	 * Value: <1, example_values> pair for the class of the generated rule
    	 */
    	context.write(new ByteArrayWritable(antecedents), new PartialSumsPairsWritable(numExamples, partialSums));
        
    }
	
	@Override
	protected void setup(Context context) throws InterruptedException, IOException{

		super.setup(context);
		
		startMs = System.currentTimeMillis();
		
		try {
			Mediator.setConfiguration(context.getConfiguration());
			Mediator.readConfiguration();
		}
		catch(Exception e){
			System.err.println("\nMAP PHASE: ERROR READING CONFIGURATION: "+e.getMessage()+"\n");
			e.printStackTrace();
			System.exit(-1);
		}
		
		// Initialize structures
		exampleStr = new String[Mediator.getNumInputVariables()+1];
		numExamples = new long[Mediator.getNumClasses()];
		partialSums = new PartialSum[Mediator.getNumClasses()][Mediator.getNumInputVariables()];
	
	}
    
}
