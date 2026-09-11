package test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Random;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JFrame;

public class CompareSeeds {
	
	public static void mainOld(String[] args) {
		int N = 50000;
		int width = 5;
		long iteration = 1l;
		int max_range = 100;
		
		long[] purest = pureseeds(N);
		long[] seeds1 = seeds1(N, width, iteration, max_range);
		long[] seeds2 = seeds2(N, width, iteration, max_range);
		long[] seeds3 = seeds3(N, width, iteration, max_range);
		long[] seeds4 = seeds4(N, width, iteration, max_range);
		long[] seeds5 = seeds5(N, width, iteration, max_range);
		long[] seeds6 = seeds6(N, width, iteration, max_range);

		displayResults(purest); // this is the goal to reach
		displayResults(seeds1); // this is terrible...
		displayResults(seeds2); // this is still pretty bad :(
		displayResults(seeds3); // this is looking good, but still not perfect
		displayResults(seeds4); // this looks great, like the reference, but we cannot see all of course
		// verdict: seeds4 looks good, but all these methods rely heavily on seeds1, which is terrible :/
		// turns out I misinterpreted what Berliner meant with 'range'... apparently it's just the maximum range
		// -- a number which in their paper is always 100, 400, 1600, or 6400
		// ALSO: it's harder to get parent seeds than previously thought: this requires storing the tree
		// -- I wanted to write an artificial tree which does not require storing the tree to traverse it
		// -- that's why I am now using seeds5 or seeds6 as they do not rely on parent seeds
		displayResults(seeds5);
		displayResults(seeds6);
	}

	/**
	 * Seed generation as described by (Berliner, 1979) in the creation of artificial game trees.<br>
	 * I had to improvise a little bit on how the ranges are determined, as it is not explicitly stated.
	 * The result will not differ much however if another method of generating ranges is used, since the 
	 * limited number of values possible does most of the damage to the randomness of the seeds already.
	 * @param length the number of nodes for which to generate seeds
	 * @param width the branching factor of the tree
	 * @param iteration the starting seed for the tree
	 * @param max_range the maximum range of a node in the tree
	 * @return the representative seed for the RNG
	 */
	static long[] seeds1(int length, int width, long iteration, int max_range) {
		long[] seeds = new long[length];
		for (int i=0; i < length; i++) {
			seeds[i] = (i + width) * (iteration + max_range);
		} return seeds;
	}
	
	/**
	 * In this version: in the spirit of Xobrist hashing, I XOR the seeds with the seed at the previous node. In practice,
	 * this would more likely be the node of the parent rather than the previous node... but that test would
	 * result in less randomness as more nodes will XOR from the same seeds. Might test this later however.
	 */
	static long[] seeds2(int length, int width, long iteration, int max_range) {
		long[] seeds = new long[length];
		Random rand = new Random(iteration);
		for (int i=0; i < length; i++) {
			seeds[i] = (i + width) * (iteration + max_range);
			if (i == 0) seeds[i] ^= rand.nextLong();
			else seeds[i] ^= seeds[i-1];
		}
		return seeds;
	}
	
	/**
	 * In this version: I rely on the pseudo-randomness of Java's built-in Random class to generate a seed by
	 * doing one iteration with the default Random starting with the seeds from {@link #seeds1(int, int, long, int)}
	 */
	static long[] seeds3(int length, int width, long iteration, int max_range) {
		long[] seeds = new long[length];
		for (int i=0; i < length; i++) {
			seeds[i] = (i + width) * (iteration + max_range);
			seeds[i] = new Random(seeds[i]).nextLong();
		} return seeds;
	}
	
	/**
	 * In this version: I combine the ideas of {@link #seeds2(int, int, long, int)} and {@link #seeds3(int, int, long, int)}
	 * by XOR-ing the seeds from seeds3 with the previous seed. This is the first properly random result.
	 * There may still be some room for improvement as we are still relying heavily on the {@link #seeds1(int, int, long, int)}
	 * seeds, which are not really all that great and may still not give unique values.
	 */
	static long[] seeds4(int length, int width, long iteration, int max_range) {
		long[] seeds = seeds3(length, width, iteration, max_range);
		Random rand = new Random(iteration);
		for (int i=0; i < length; i++)
			seeds[i] ^= i==0? rand.nextLong() : seeds[i-1];
		return seeds;
	}
	
	static long[] seeds5(int length, int width, long iteration, int max_range) {
		long[] seeds = new long[length];
		for (int i=0; i < length; i++) {
			Random rand = new Random((i + width)*(iteration + max_range));
			seeds[i] = rand.nextLong();
		}
		return seeds;
	}
	
	static long[] seeds6(int length, int width, long iteration, int max_range) {
		long[] seeds = new long[length];
		for (int i=0; i < length; i++) {
			Random rand = new Random((i + width)*(iteration + max_range));
			seeds[i] = rand.nextLong() ^ rand.nextLong();
		}
		return seeds;
	}
	
	/**
	 * @param length number of values to generate
	 * @param a starting seed for generator A
	 * @param b starting seed for generator B
	 * @return A pretty random number generated from XOR-ing the numbers generated by two pseudo-random number generators.
	 */
	static long[] pureseeds(int length, long a, long b) {
		Random A = new Random(a);
		Random B = new Random(b);
		long[] seeds = new long[length];
		for (int i=0; i < length; i++) {
		    seeds[i] = A.nextLong() ^ B.nextLong();
		} return seeds;
	}
	/**
	 * Same as {@link #pureseeds(int, long, long)}, where A and B are initialized with system time in nano-seconds
	 * and system time in milli-seconds.
	 */
	static long[] pureseeds(int length) {
		return pureseeds(length, System.nanoTime(), System.currentTimeMillis());
	}
	
	static void fillBits(long[] bits, long x) {
	    for (int i=0; i < 64 && i < bits.length; i++) {
	        bits[i] += (x & (1l << i)) == 0 ? 0 : 1;
	    }
	}
	static void fillBits(long[] bits, long[] xs) {
		for (int i=0; i < xs.length; i++) fillBits(bits, xs[i]);
	}
	/**
	 * @param xs an array holding values from a pseudo-random source
	 * @return an array holding the frequency of each of 64-bits in a {@code long} as found in the given array.
	 */
	static long[] inspect(long[] xs) {
	    long[] res = new long[64];
	    fillBits(res, xs);
	    return res;
	}
	
	/**
	 * @param xs an array holding values from a pseudo-random source
	 * @return a double array holding two matrices related to the correlations between bits in the number from the given array.
	 * The first (64x64) matrix gives the number of times that two specific bits were the same for each of the 64x64
	 * combinations of bits. The second (64x64) matrix gives the total sum of differences of two specific bits for each of
	 * the 64x64 combinations of bits. Both matrices sum values as calculated from numbers occurring in the given array.
	 * This method does <b>not</b> actually compute any kind of correlation relating to the given array, as it only ever
	 * looks at the singular values found in the array, and not at the relations between these values. It will still show
	 * interesting patterns if a list is not random however, since the correlations between bits within each value will not
	 * even out in a list with only a small set of different values. A non-random list could be constructed to fool this
	 * method completely however.
	 */
	static long[][] checkCorrs(long[] xs) {
	    long[][] corrs = new long[128][64];
	    
	    for (int i=0; i < corrs.length; i++) {
	        for (int j=0; j < corrs[i].length; j++) {
	            for (int k=0; k < xs.length; k++) {
	            	if (i >= 64)
	            		corrs[i][j] += ((xs[k] & (1l << (i-64)))==0?0:1) - ((xs[k] & (1l << j))==0?0:1);
	            	else if (i != j)
	                	corrs[i][j] += (((xs[k] & (1l << i))==0?0:1) - ((xs[k] & (1l << j))==0?0:1)) == 0 ? 0 : 1;
	                else
	                	corrs[i][j] += (xs[k] & (1l << i)) == 0 ? 0 : 1;
	            }
	        }
	    }
	    return corrs;
	}
	
	static void displayResults(long[] xs) {
		long[] inspected = inspect(xs);
		for (int i=0; i < inspected.length; i++) {
			System.out.format("%03d ", inspected[i] / (xs.length / 999));
		}
		System.out.println();
		
		long[][] result = checkCorrs(xs);
		for (int i=0; i < result.length; i++) {
			for (int j=0; j < result[i].length; j++) {
				System.out.format("% 4d", result[i][j] / (xs.length / 99));
			}
			System.out.println();
		}
		System.out.println();
	}
	
	public static void main(String[] args) {
		displayRNG();
	}
	
	public static void displayRNG() {
		long N = 100000;
		int R = 1000;
		boolean c = false;
		Random r = new Random();
		// nextInt(R):
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); return 1. - ((1. + r.nextInt(R)) / R);
			}, c), String.format("[0,%d)/%d | 1st call", R, R));
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.nextLong(); return 1. - ((1. + r.nextInt(R)) / R);
			}, c), String.format("[0,%d)/%d | 2nd call", R, R));
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong()); return 1. - ((1. + r.nextInt(R)) / R);
			}, c), String.format("[0,%d)/%d | re-seed once", R, R));
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong()); r.setSeed(r.nextLong()); return 1. - ((1. + r.nextInt(R)) / R);
			}, c), String.format("[0,%d)/%d | re-seed twice", R, R));
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong() ^ r.nextLong()); return 1. - ((1. + r.nextInt(R)) / R);
			}, c), String.format("[0,%d)/%d | re-seed XOR", R, R));
		// nextDouble():
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); return r.nextDouble();
			}, c), "[0,1) | 1st call");
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.nextLong(); return r.nextDouble();
			}, c), "[0,1) | 2nd call");
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong()); return r.nextDouble();
			}, c), "[0,1) | re-seed once");
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong()); r.setSeed(r.nextLong()); return r.nextDouble();
			}, c), "[0,1) | re-seed twice");
		show(getNumberDisplay(N, R, s -> {
				r.setSeed(s); r.setSeed(r.nextLong() ^ r.nextLong()); return r.nextDouble();
			}, c), "[0,1) | re-seed XOR");
	}
	
	/**
	 * Gives a JComponent to display the distribution of values generated from set seeds
	 * @param max_seed the maximum seed up to which to include (includes 0, excludes max_seed)
	 * @param range the range of values the PRNG is expected to produce, fill in with arbitrarily large
	 *         value if unknown.
	 * @param PRNG a function which takes a Long as *seed* and outputs the first value in [0, 1) generated
	 * @param use_colors if {@code true} colors pixels one of 4 colours based on the mod-2 and mod-3 values of the seed.
	 *         Colors pixels all black or white if {@code false}
	 * @return a JComponent which displays the values generated by the PRNG
	 */
	static JComponent getNumberDisplay(long max_seed, long range, Function<Long, Double> PRNG, boolean use_colors) {
	    return new JComponent() {
			private static final long serialVersionUID = 1L;
			
			@Override
		    public void paintComponent(Graphics g) {
		        Graphics2D g2d = (Graphics2D) g;
		        g2d.setColor(Color.black);
		        g2d.fillRect(0, 0, getWidth(), getHeight());
		        g2d.setColor(Color.white);
		        Color[] colors = new Color[] {Color.magenta, Color.blue, Color.red, Color.green};
		        for (long l=0; l < max_seed; l++) {
		            double val = PRNG.apply(l);
		            int posX = (int) ((((double) l) / max_seed) * getWidth());
		            int pW = (int) (((l + 1.) / max_seed) * getWidth()) - posX;
		            int posY = (int) (val * getHeight());
		            int pH = (int) ((val + 1./range) * getHeight()) - posY;
		            if (use_colors) {
		            	int c_id = (l%2==0 ? 0 : 1) + (l%3==0 ? 0 : 2);
		            	g2d.setColor(colors[c_id]);
		            }
		            g2d.fillRect(posX, posY, pW > 0 ? pW : 1, pH > 0 ? pH : 1);
    }}};}
	JComponent getNumberDisplay(long max_seed, long range, Function<Long, Double> PRNG) {
		return getNumberDisplay(max_seed, range, PRNG, false); }
	JComponent getNumberDisplay(long max_seed, Function<Long, Double> PRNG, boolean use_colors) {
		return getNumberDisplay(max_seed, Long.MAX_VALUE, PRNG, use_colors); }
	JComponent getNumberDisplay(long max_seed, Function<Long, Double> PRNG) {
		return getNumberDisplay(max_seed, PRNG, false); }
	
	static void show(JComponent j, String name) {
	    JFrame frame = new JFrame(name);
	    frame.setSize(500,500);
	    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	    frame.add(j);
	    frame.setVisible(true);
	}
}
