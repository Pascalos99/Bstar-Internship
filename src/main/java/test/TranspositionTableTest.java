package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TranspositionTableTest {

	public static void main(String[] args) throws IOException, InterruptedException {
		int[] uniqueness = {64, 32, 28, 24, 20, 16};
		long[] num_uniques = {Long.MAX_VALUE, 1l<<32, 1l<<28, 1l<<24, 1l<<20, 1l<<16};
		
		String input_filename = "input_data.csv";
		String output_filename = "output_data.csv";
		boolean first_print_in_file = new File(output_filename).createNewFile();
		final FileWriter writer = new FileWriter(output_filename, true);
		if (first_print_in_file) writer.append("uniqueness,bitsize,total writes,total reads,overwrite chance,write collisions,read collisions\n");
		BufferedReader reader = new BufferedReader(new FileReader(input_filename));
		try {
			ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			reader.readLine(); // skip the first line as it just contains headers:
			// bitsize | writes | reads | write collisions | read collisions | overwrite-chance
			for(String line; (line = reader.readLine()) != null;) {
				String[] values = line.split(",");
				int bitsize = Integer.parseInt(values[0]);
				long writes = Long.parseLong(values[1]);
				long reads = Long.parseLong(values[2]);
				double overwrite_chance = Double.parseDouble(values[5]) / 1000.;
				exec.execute(() -> {
					for (int i=0; i < uniqueness.length; i++) {
						long num_unique_states = num_uniques[i];
						TranspositionTableTest ttt = new TranspositionTableTest(
								bitsize, writes, reads, overwrite_chance, num_unique_states);
						ttt.destination = "testdata"+File.separatorChar+(""+uniqueness[i])+File.separatorChar+"run";
						System.out.format("[ ] Initiated run (%02d<-%02d)\n", bitsize, uniqueness[i]);
						try {
							ttt.run(false, false, false);
							System.out.format("[X] Completed run (%02d<-%02d)\n", bitsize, uniqueness[i]);
						} catch (OutOfMemoryError e) {
							System.out.format("[0] Failed run (%02d<-%02d)\n", bitsize, uniqueness[i]);
						}
						synchronized(writer) {
							try {
								writer.append(String.format("%d,%d,%d,%d,%.2f,%d,%d\n",
										uniqueness[i],bitsize,writes,reads,overwrite_chance*1000,ttt.write_colls,ttt.read_colls));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				});
			}
			
			exec.shutdown();
			if (!exec.awaitTermination(60, TimeUnit.MINUTES)) {
				throw new InterruptedException("Execution took too long");
			}
		} finally {
			reader.close();
			writer.close();
		}
	}
	
	public TranspositionTableTest(int bitsize, long total_writes, long total_reads, double overwrite_chance) {
		this(bitsize, total_writes, total_reads, overwrite_chance, Long.MAX_VALUE);
	}
	public TranspositionTableTest(int bitsize, long total_writes, long total_reads, double overwrite_chance, long num_unique_states) {
		this.bitsize = bitsize;
		this.write_attempts = total_writes;
		this.read_attempts = total_reads;
		this.overwrite_chance = overwrite_chance;
		this.num_unique_states = num_unique_states;
		setSeed(System.currentTimeMillis());
		reset();
	}
	
	private long seed;
	public void setSeed(long seed) {
		this.seed = seed;
	}
	
	public void reset() {
		write_colls = 0l;
		read_colls = 0l;
	}
	
	// SETTINGS
	final long num_unique_states;
	final int bitsize;
	final long write_attempts;
	final long read_attempts;
	final double overwrite_chance;
	
	// DEBUG
	public int number_of_print_updates = 1000;
	public double waittime_growth_factor = 2.;
	public String destination = "testfile";
	
	// OUTPUT
	public long write_colls, read_colls;
	
	public void run(boolean print_progress, boolean write_to_file, boolean show_plot) {
		/* This test will simulate what may happen during search
		 * with the number of read-requests and write-requests taken
		 * from an alpha-beta search on the game of Boku. This test
		 * will look at different bit-sizes for the keys of the TT
		 */
		long num_lines = 0l, next_line = 0l, threshold;
		String filename = destination+bitsize+".csv";
		
		FileWriter writer = null;
		if (write_to_file || show_plot) {
			try {
				new File(filename).getParentFile().mkdirs();
				writer = new FileWriter(filename, false);
				writer.write("iteration,writes,reads,write collisions,read collisions\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// SIMULATION
		Random random = new Random(seed);
		Hashtable<Long, Long> TT = new Hashtable<>();
		double write_chance;
		long iteration = 0l;
		// NOTE that 'read_attempts' only counts attempts where the key to read was actually found
		for (long writes=0l, reads=0l; writes < write_attempts || reads < read_attempts; iteration++) {
			write_chance = ((double)write_attempts-writes) / (write_attempts-writes + read_attempts-reads);
			
			long hash;
			if (num_unique_states == Long.MAX_VALUE) {
				hash = random.nextLong();
			} else {
				Random x = new Random(random.nextLong(num_unique_states));
				x.nextLong();
				hash = x.nextLong();
			}
			
			long index = (int) hash & (-1 >>> (32 - bitsize));
			boolean write = (writes < write_attempts) && ((reads >= read_attempts) || (random.nextDouble() < write_chance));
			
			if (write) {
				writes++;
				Long prev = TT.put(index, hash);
				if (prev != null) {
					write_colls++;
					if (random.nextDouble() >= overwrite_chance) TT.put(index, prev);
				}
			} else {
				Long val = TT.get(index);
				if (val != null) {
					reads++;
					if ((long)val != hash) read_colls++;
				}
			}
			// WRITE TO FILE
			if ((write_to_file || show_plot) && iteration==next_line) {
				threshold = Math.max((long) Math.ceil(waittime_growth_factor * iteration / (1+num_lines)), 1l); 
				next_line = iteration + threshold;
				if (iteration%threshold == 0) {
					num_lines++;
					try {
						writer.append(String.format("%d,%d,%d,%d,%d\n", iteration, writes, reads, write_colls, read_colls));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			// PRINT PROGRESS
			long totes = writes + reads;
			if (print_progress && (totes % ((write_attempts + read_attempts)/number_of_print_updates) == 0)) {
				System.out.format(" * write-collisions: %d/%d\n * read-collisions: %d/%d\n\t%.2f%%\ti=%d\n",
						write_colls, writes, read_colls, reads,
						100d * totes / (write_attempts + read_attempts), iteration);
			}
		}
		if (print_progress) {
			System.out.format("write-collisions: %d/%d\nread-collisions: %d/%d\n",
					write_colls, write_attempts, read_colls, read_attempts);
		}
		if (write_to_file || show_plot) {
			try {
			    writer.close();
			    if (show_plot) {
				    String command = "python show_plot.py "+filename;
				    Process p = Runtime.getRuntime().exec(command);
			    }
			    if (!write_to_file) {
			    	new File(filename).delete();
			    }
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
