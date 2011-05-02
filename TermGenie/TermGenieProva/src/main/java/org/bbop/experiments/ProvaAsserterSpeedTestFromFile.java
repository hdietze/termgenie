package org.bbop.experiments;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;

import ws.prova.api2.ProvaCommunicatorImpl;
import ws.prova.exchange.ProvaSolution;

public class ProvaAsserterSpeedTestFromFile {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Runtime runtime = Runtime.getRuntime ();
		Timer start = new Timer();
		ProvaCommunicatorImpl prova = new ProvaCommunicatorImpl("prova",
				"src/main/esources/speed/speed100000.pro", ProvaCommunicatorImpl.SYNC);

		prova.setPrintWriter(new PrintWriter(new Writer() {
			
			@Override
			public void write(char[] cbuf, int off, int len) throws IOException {
				// do nothing
			}
			
			@Override
			public void flush() throws IOException {
				// do nothing
			}
			
			@Override
			public void close() throws IOException {
				// do nothing				
			}
		}));
		
		
		String startTime = start.getStop();
		System.out.println("Startup Time: "+startTime);
		
		System.gc();
		System.gc();
		System.gc();
		System.out.println("Memory: "+getMemoryConsumption(runtime)+" MB");
		
		Timer reasoning = new Timer();
		String inputRules = ":- solve(isTTriple(X, subClassOf, 'ID:1')).";
		BufferedReader queryRules = new BufferedReader(new StringReader(inputRules));
		List<ProvaSolution[]> consultSync = prova.consultSync(queryRules, "query", new Object[]{});
		String reasoningTime = reasoning.getStop();
		System.out.println("Resoning Time "+reasoningTime);
		prova.stop();
	}
	
	private static class Timer {
		private long start;
		
		public Timer() {
			start = System.currentTimeMillis();
		}
		
		public String getStop() {
			long end = System.currentTimeMillis();
			return format(end-start);
		}
		
		protected static String format(long elapsedTime)
		{
			String mseconds = String.format("%03d", elapsedTime % 1000);
			elapsedTime = elapsedTime / 1000;  
			String format = "%02d";
			String seconds = String.format(format, elapsedTime % 60);  
			String minutes = String.format(format, (elapsedTime % 3600) / 60);  
			String hours = String.format(format, elapsedTime / 3600);  
			String time =  hours + ":" + minutes + ":" + seconds + "."+ mseconds;
			return time;
		}
	}
	
	private static String getMemoryConsumption(Runtime runtime) {
		return Long.toString((runtime.totalMemory () - runtime.freeMemory ()) / (1024L * 1024L));
	}

}
