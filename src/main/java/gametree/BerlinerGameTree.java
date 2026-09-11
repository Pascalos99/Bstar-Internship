package gametree;

import static gametree.GenerationSettings.berliner;
import static java.lang.Math.min;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

import gametree.GenerationSettings.Style;

public class BerlinerGameTree implements GameTree<BerlinerGameTree.BerlinerNode> {
	
	public final long iter;
	public final int width;
	public final long range;
	public final int num_alts;
	
	public final GenerationSettings variant;
	
	private final double log_w;
	
	private static double LOG_2 = Math.log(2.0);
	
	private Random r;
	
	public BerlinerGameTree(long iteration, int tree_width, long range_at_root) {
		this(iteration, tree_width, range_at_root, 2);
	}
	public BerlinerGameTree(long iteration, int tree_width, long range_at_root, int number_of_alts) {
		this(iteration, tree_width, range_at_root, number_of_alts, berliner);
	}
	public BerlinerGameTree(long iteration, int tree_width, long range_at_root, GenerationSettings generation_variant) {
		this(iteration, tree_width, range_at_root, 2, generation_variant);
	}
	public BerlinerGameTree(long iteration, int tree_width, long range_at_root, int number_of_alts, GenerationSettings generation_variant) {
		iter = iteration;
		width = tree_width;
		range = range_at_root;
		num_alts = Math.max(2, number_of_alts);
		variant = generation_variant;
		
		log_w = Math.log(width);
		
		r = new Random();
	}
	
	@Override
	public BerlinerNode root() {
		// name == 0 refers to the ROOT node
		return new BerlinerNode(BigInteger.ZERO);
	}
	
	public BerlinerNode get(BigInteger name) {
		return new BerlinerNode(name);
	}
	
	public String describe(boolean includeSeed) {
		return String.format("%s (%d)[%5d]%s", variant, width, range, includeSeed ? "@"+iter : "");
	}
	
	public class BerlinerNode implements Node<BerlinerNode> {
		
		// BigInteger prevents integer overflow at deep depths
		public final BigInteger name;
		private long opt, pes;
		private boolean bounds_set;
		
		private BerlinerNode(BigInteger name, long opt, long pes, boolean bounds_set) {
			this.name = name;
			this.opt = opt;
			this.pes = pes;
			this.bounds_set = bounds_set;
		}
		private BerlinerNode(BigInteger name, long opt, long pes) {
			this(name, opt, pes, true);
		}
		private BerlinerNode(BigInteger name) {
			this(name, 0, 0, false);
		}
		
		public BigInteger parentname() {
			// according to the formula: "parent.name = (name - 1) / width"
			return name.subtract(BigInteger.ONE).divide(BigInteger.valueOf(width));
		}
		public long depth() {
			// depth = floor[ log( name*(width-1)+1 ) / log( width ) ];
			
			// I'm using BigInteger, to allow for deeper trees!
			
			BigInteger val = name.multiply(BigInteger.valueOf(width-1)).add(BigInteger.ONE);
			
			// thanks to https://stackoverflow.com/questions/6827516/logarithm-for-biginteger
			// for figuring out an easy way to get the logarithm of BigInteger:
	        int blex = val.bitLength() - 977;
	        if (blex > 0)
	            val = val.shiftRight(blex);
	        double res = Math.log(val.doubleValue());
	        return (long) Math.floor((blex > 0 ? res + blex * LOG_2 : res) / log_w);
		}
		
		@Override
		public BerlinerNode parent() {
			// name == 0 refers to the ROOT node
			if (name.signum() == 0) return null;
			return new BerlinerNode(parentname());
		}
		
		private long getDelta(long pes, long opt) {
			double deltaD = (variant.growth - 1) * safeSum(opt, -pes);
			long delta = (long) Math.floor(deltaD);
			delta = safeSum(delta, r.nextDouble() < (deltaD - delta) ? 1 : 0);
			return delta;
		}
		
		private long minV(long[] values) {
			long minv = values[0];
			for (int i=1; i < values.length; i++) minv = Long.min(values[i], minv);
			return minv;
		}
		private long maxV(long[] values) {
			long maxv = values[0];
			for (int i=1; i < values.length; i++) maxv = Long.max(values[i], maxv);
			return maxv;
		}
		
		/**
		 * @return {@code x + p} unless overflow or underflow occurs, in which case 
		 *  {@link Long.MAX_VALUE} or {@link Long.MIN_VALUE} respectively.
		 */
		public static long safeSum(long x, long p) {
			long r = x + p;
			if (p > 0 && r < x) return Long.MAX_VALUE;
			if (p < 0 && r > x) return Long.MIN_VALUE;
			return r;
		}

		@Override
		public List<BerlinerNode> children() {
			if (!bounds_set) setBounds();
			if (opt == pes) {
				// if this node has bounds of 0, then it is thus a terminal state with no children;
				// this returns an empty list:
				return List.of();
			}
			// seed created like so:
			// (parentname + width) * (iteration * range)
			// BUT: if we use parentname as is, which is possibly > 2^63, we get a seed which can be
			//      more than 64 bits long -- there is no predictable PRNG in Java which takes a seed like this..
			//      --> we need a 64-bit seed, but how to get it?
			//   easy way: truncate the parentname or resulting seed by the LAST 64 bits.
			//      this works because, for close relatives, differences in names will only be in the last 64-bits;
			//      therefore, we don't need to consider the first few bits, only the last 64.
			//  truncating the parentname is more efficient, because then we don't have to use as many BigInteger operations
			// so:
			long seed = (name.longValue() + width) * (iter + range);
			r.setSeed(seed);
			r.setSeed(r.nextLong());
			
			boolean found_node_with_parent_opt = false;
			boolean found_node_with_parent_pes = false;
			
			// for "BaseRulesBefore":
			// pre-determined child-index to ensure best pessimistic and optimistic values within parent range:
			int ci = variant.style == Style.BaseRulesBefore ? r.nextInt(width) : -1;
			
			BerlinerNode[] children = new BerlinerNode[width];
			for (int i=0; i < width; i++) {
				BigInteger child_name = name.multiply(BigInteger.valueOf(width)).add(BigInteger.valueOf(i + 1));
				long[] values = new long[num_alts];
				long minv, maxv, delta;
				switch (variant.style) {
				case BaseRulesAfter:
					delta = getDelta(pes, opt);
					if (variant.preserve_sign && pes > 0) delta = min(safeSum(pes,-1), delta);
					for (int j=0; j < values.length; j++)
						values[j] = -r.nextLong(safeSum(pes, -delta), safeSum(opt,1));
					if (r.nextFloat() < variant.force_value_in_parent_range_chance)
						values[0] = -r.nextLong(pes, safeSum(opt,1));
					minv = minV(values); maxv = maxV(values);
					// make sure pes value of parent stays within bounds:
					if (-minv >= pes && -maxv >= pes) found_node_with_parent_pes = true;
					break;
				case BaseRulesBefore:
					delta = getDelta(pes, opt);
					if (variant.preserve_sign && pes > 0) delta = min(safeSum(pes,-1), delta);
					if (i == ci) {
						for (int j=0; j < values.length; j++)
							values[j] = -r.nextLong(pes, safeSum(opt,1));
					} else {
						for (int j=0; j < values.length; j++)
							values[j] = -r.nextLong(safeSum(pes, -delta), safeSum(opt,1));
					}
					if (r.nextFloat() < variant.force_value_in_parent_range_chance)
						values[0] = -r.nextLong(pes, safeSum(opt,1));
					break;
				case Legacy:
					for (int j=0; j < values.length; j++) {
						long lower = pes > 0 ? 1l : safeSum(safeSum(pes,opt),1);
						values[j] = -r.nextLong(lower, safeSum(opt,1));
					}
					if (r.nextFloat() < variant.force_value_in_parent_range_chance)
						values[0] = -r.nextLong(pes, safeSum(opt,1));
					minv = minV(values); maxv = maxV(values);
					// make sure pes value of parent stays within bounds:
					if (-minv >= pes && -maxv >= pes) found_node_with_parent_pes = true;
					break;
				case BerlinerSplit:
					long halfway_point = safeSum(pes, safeSum(opt, -pes) / 2); // midway-point = pes + (opt - pes) / 2
					minv = -r.nextLong(halfway_point, safeSum(opt,1));
					maxv = halfway_point <= pes ? -pes : -r.nextLong(pes, halfway_point);
					values[0] = minv;
					for (int j=1; j < values.length; j++) values[j] = maxv;
					if (minv == -opt || maxv == -opt) found_node_with_parent_opt = true;
					if (minv == -pes || maxv == -pes) found_node_with_parent_pes = true;
					break;
				case Palay, Berliner:
				default:
					for (int j=0; j < values.length; j++)
						values[j] = -r.nextLong(pes, safeSum(opt,1));
					minv = minV(values); maxv = maxV(values);
					if (minv == -opt || maxv == -opt) found_node_with_parent_opt = true;
					if (minv == -pes || maxv == -pes) found_node_with_parent_pes = true;
				}
				children[i] = new BerlinerNode(child_name, maxV(values), minV(values));
			}
			switch (variant.style) {
			case Legacy, BaseRulesAfter:
				if (!found_node_with_parent_pes) {
					ci = r.nextInt(width);
					long[] values = new long[num_alts];
					for (int j=0; j < values.length; j++)
						values[j] = -r.nextLong(pes, safeSum(opt,1));
					children[ci].pes = minV(values);
					children[ci].opt = maxV(values);
				}
				break;
			case Palay, Berliner, BerlinerSplit:
				if (!found_node_with_parent_pes) children[r.nextInt(width)].opt = -pes;
				if (!found_node_with_parent_opt) children[r.nextInt(width)].pes = -opt;
				break;
			default:
			}
			
			if (variant.style == Style.Palay) {
				for (int i=0; i < children.length; i++)
					if (safeSum(children[i].opt, -children[i].pes) <= 2) {
						children[i].opt = children[i].pes = safeSum(children[i].opt, children[i].pes) / 2; }
			}
			
			assert Arrays.stream(children).anyMatch(c -> 
					-c.opt <= opt && -c.opt >= pes && -c.pes <= opt && -c.pes >= pes)
			: String.format("best score %d out of bounds [%d, %d]",
					Arrays.stream(children).map(c -> -c.opt).reduce(Long::max).get(), pes, opt);
			
			return List.of(children);
		}
		
		public void setBounds() {
			if (bounds_set) return;
			if (name.signum() == 0) {
				// "if this node is the ROOT node:"
				opt = range;
				pes = 1;
				bounds_set = true;
				return;
			}
			// The following recursive call is only relevant when using RANDOM-ACCESS to search the tree.
			//  -- while searching the tree like normal (generating children starting from root and so on) this will not be executed.
			BerlinerNode parent = parent();
			List<BerlinerNode> siblings = parent.children();
			if (siblings.size() <= 0) {
				opt = -parent.pes;
				pes = -parent.opt;
			} else {
				BerlinerNode copy = siblings.stream().filter(s -> s.name.equals(name)).findAny().get();
				opt = copy.opt;
				pes = copy.pes;
			}
			bounds_set = true;
		}
		
		@Override
		public double optimistic() {
			if (!bounds_set) setBounds();
			return opt;
		}

		@Override
		public double pessimistic() {
			if (!bounds_set) setBounds();
			return pes;
		}
		
		@Override
		public String toString() {
			return String.format("%s: [%s,%s]", name.toString(36),
					pes==0 ? "?" : String.format("% 3d", pes),
					opt==0 ? "?" : String.format("% 3d", opt));
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		long N = 100;
		long timer = 5000;
		GenerationSettings variant = berliner; // basic // legacy
		int branching_factor = 2;
		int range = 2;
		long initial_seed = 1l;
		boolean debug = true;
		boolean write_to_file = true;
		boolean parallel = true;
		
		final BufferedWriter bw = write_to_file ? new BufferedWriter(new FileWriter("test.csv")) : null;
		if (write_to_file) bw.write("seed,width,range,depth,terminated\n");
		
		List<Supplier<Long>> tasks = new ArrayList<>((int)N);
		
		long t0 = System.currentTimeMillis();
		
		for (long i=initial_seed; i < initial_seed+N; i++) {
			final BerlinerGameTree tree = new BerlinerGameTree(i, branching_factor, range, variant);
			final long seed = i;
			tasks.add(() -> {
				Long d = SearchGameTree.probeDepth(tree, timer);
				
				if (debug) {
					if (d > 0) System.out.print("terminated at ");
					System.out.println(Math.abs(d));
				}
				if (write_to_file) {
					synchronized(bw) {
						try {
							bw.append(String.format("%d,%d,%d,%d,%s\n", seed, branching_factor, range, Math.abs(d), d < 0 ? "no" : "yes"));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				return Math.abs(d);
			});
		}
		
		final long identity = 0l;
		final BinaryOperator<Long> op = Long::sum;
		
		long result = (parallel ? tasks.parallelStream() : tasks.stream()).map(Supplier::get).reduce(identity, op);
		System.out.println("on average getting to depth "+(((double)result)/N));
		
		if (write_to_file) bw.close();
		
		System.out.println("total time used is "+(System.currentTimeMillis() - t0)/1000.+" seconds");
	}
	
	public static void exploreTree(long seed, int branching_factor, long range) throws IOException {
		BerlinerGameTree tree = new BerlinerGameTree(seed, branching_factor, range);
		BerlinerNode current = tree.root();
		
		long depth = SearchGameTree.probeDepth(tree, 1000);
		System.out.println("Probed tree depth at "+(depth<0?">>":"")+Math.abs(depth));
		
		System.out.println("Input a valid number for the next node to be explored (you can access ANY node)"
				+ " and write \"exit\" or \"stop\" to exit.");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		for (String input = ""; !input.contains("exit") && !input.contains("stop"); input = br.readLine()) {
			if (!input.equals("")) {
				try {
					BigInteger explore = new BigInteger(input, 36);
					if (explore.signum() >= 0) current = tree.get(explore);
				} catch (NumberFormatException e) {
					System.out.println("please input a valid number or say \"exit\" or \"stop\" to exit.");
					continue;
				}
			}
			current.setBounds();
			System.out.println(current+" (d="+current.depth()+")"+" -> ");
			for (BerlinerNode child : current.children()) {
				child.setBounds();
				System.out.print(child+"\t");
			} System.out.print("\nnext: ");
		}
	}
}
