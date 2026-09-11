package test;

import static algorithm.BstarBasic.*;
import static gametree.GenerationSettings.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import algorithm.BstarBasic;
import algorithm.BstarDB;
import algorithm.GameTreeSearch;
import algorithm.SearchAlgorithm;
import algorithm.SearchAlgorithm.Metric;
import gametree.BerlinerGameTree;
import gametree.GameTree;
import gametree.GameTree.Result;
import gametree.GenerationSettings;
import gametree.GenerationSettings.Style;
import gametree.ResultNode;

public class CollectData {
	private static int num_completed = 0;
	private static int num_prints = 1000;
	private static int num_total = 0;
	private static void recordProgress(int set_total) {
		num_total = set_total;
	}
	private static void recordProgress(String message) {
		num_completed++;
		if (num_completed % (num_total / num_prints) == 0) {
			System.out.format("%4d/%d : %s of %5d trees out of %d\n", (num_prints * num_completed) / num_total, num_prints, message, num_completed, num_total);
		}
	}
	
	@SuppressWarnings("unused")
	public static void main(String[] args) {
		GameTreeSearch.limits.maxDepth = Long.MAX_VALUE;
		GameTreeSearch.limits.maxEvaluated = 500000;
		GameTreeSearch.limits.maxExpanded = Long.MAX_VALUE;
		GameTreeSearch.limits.useLimits = true; // toggle to `false` to use experimental stopping
		
		long initial_seed = 5000000;
		int nr_of_trees_per_pair = 10;
		double[] growthFactors = {0.75, 1, 1.4, 2, 3};
		float[] force_chances = {0f};
		int[] num_alts = {2, 3, 4, 5, 6, 7, 8, 9, 10};
		long[] ranges = {100, 800, 6400};
		int[] branching_factors = {3,4,5,6,7,8,9,10};
		
		String filename_of_previously_computed_runs = null;//"data/experiments/collected_data_02_07.csv";
		long[] skip_seeds = new long[0]; String[][] skip_algs = new String[0][];
		if (filename_of_previously_computed_runs != null) {
			long[][] _skip_seeds = new long[1][];
			String[][][] _skip_algs = new String[1][][];
			getAlreadyComputedRuns(filename_of_previously_computed_runs, _skip_seeds, _skip_algs);
			skip_seeds = _skip_seeds[0];
			skip_algs = _skip_algs[0];
		}
		
		String filename = "data/experiments/collected_data_03_07.csv";
		
		ArrayList<GenerationSettings> settings = new ArrayList<>(List.of(new GenerationSettings[]{
				berliner, palay, berlinerSplit }));
		for (Float fc : force_chances) {
			settings.add(new GenerationSettings(Style.Legacy, 1, fc, true));
			for (Double g : growthFactors) {
//				settings.add(new GenerationSettings(Style.BaseRulesBefore, g, fc, true));
				settings.add(new GenerationSettings(Style.BaseRulesBefore, g, fc, false));
//				settings.add(new GenerationSettings(Style.BaseRulesAfter, g, fc, true));
				settings.add(new GenerationSettings(Style.BaseRulesAfter, g, fc, false));
			}
		}
		
		// takes in the branching factor *b* of the tree as an argument
		Map<String, Function<Integer, SearchAlgorithm>> algos = new HashMap<>();
		algos.put("always-PB", b -> new BstarBasic(PROVEBEST, true));
		algos.put("prefer-DR", b -> new BstarBasic(DISPROVEREST, true));
		algos.put("randomize", b -> new BstarBasic(RANDOM, true));
		algos.put("alternate", b -> new BstarBasic(ALTERNATE(), true));
		algos.put("berlin-2D", b -> new BstarBasic(B_2D, true));
		algos.put("berlin-3D", b -> new BstarBasic(B_3D, true));
		algos.put("berlin-AD", b -> new BstarBasic(B_AD, true));
		algos.put("berlin-2R", b -> new BstarBasic(B_2R, true));
		algos.put("berlin-3R", b -> new BstarBasic(B_3R, true));
		algos.put("berlin-AR", b -> new BstarBasic(B_AR, true));
		algos.put("DB-AR-0.8", b -> new BstarDB(B_AR, true, 0.8/b));
		algos.put("DB-AR-1.0", b -> new BstarDB(B_AR, true, 1.0/b));
		algos.put("DB-AR-1.4", b -> new BstarDB(B_AR, true, 1.4/b));
		algos.put("DB-AR-1.6", b -> new BstarDB(B_AR, true, 1.6/b));
		algos.put("DB-AR-2.0", b -> new BstarDB(B_AR, true, 2.0/b));
		algos.put("altern-DB", b -> new BstarDB(BstarBasic.ALTERNATE(), true, 1.4/b));
		
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
			bw.append("style,algorithm,growth,PS,num_alts,force_chance,seed,range,width,seconds,depth,evaluated,expanded,intractable,solution,depth1Pes,depth2Opt,sepValue\n");
			// we save all relevant information for the setup of a run, along with some metrics, and all necessary info about the solution it found
			
			int number_of_trees = (settings.size() - force_chances.length) * ranges.length * branching_factors.length * (num_alts.length) *
					nr_of_trees_per_pair + force_chances.length * ranges.length * branching_factors.length * nr_of_trees_per_pair;
//			int number_of_trees = force_chances.length * ranges.length * branching_factors.length * nr_of_trees_per_pair;
			recordProgress(number_of_trees);
			System.out.println("Starting progress on "+number_of_trees+" trees");
			
			long seed = initial_seed;
			for (GenerationSettings setting : settings)
			for (int R=0; R < ranges.length; R++)
			for (int b=0; b < branching_factors.length; b++)
			for (int k=0; (k < num_alts.length && setting.style != Style.BerlinerSplit) || k == 0; k++)
			for (int i=0; i < nr_of_trees_per_pair; i++) {
				final long _seed = seed++;
				final int _b = b, _R = R, _k = k;
				
				final int skip_seed_index = Arrays.binarySearch(skip_seeds, _seed);
				final String[][] algs_skip = skip_algs;
				
				exec.submit(() -> {
					BerlinerGameTree tree = new BerlinerGameTree(_seed, branching_factors[_b], ranges[_R], num_alts[_k], setting);
					algos.forEach((alg_name, algo) -> {
						
						if (skip_seed_index >= 0) {
							String[] skip_these = algs_skip[skip_seed_index];
							for (int J=0; J < skip_these.length; J++)
								if (skip_these[J].equals(alg_name)) return;
						}
						
						Metric r = null;
						Instant A = Instant.now();
						try {
							r = algo.apply(_b).search(tree);
						} catch (RuntimeException e) {
							e.printStackTrace();
							return;
						}
						Instant B = Instant.now();
						try {
							synchronized (bw) {
								Result<? extends ResultNode<?>> B2 = GameTree.findBest2(r.root().children());
								double bestPes = Double.NEGATIVE_INFINITY;
								for (ResultNode<?> n : r.root().children()) bestPes = Math.max(bestPes, -n.optimistic());
								
								bw.append(String.format("%s,%s,%f,%s,%d,%f,%d,%d,%d,%f,%d,%d,%d,%s,%s,%d,%d,%f\n",
										tree.variant.style, alg_name, tree.variant.growth, tree.variant.preserve_sign ? "yes" : "no",
										tree.num_alts, tree.variant.force_value_in_parent_range_chance, tree.iter, tree.range,
										tree.width, Duration.between(A, B).toMillis()/1000d, r.maxDepth(), r.numEvaluated(), r.numExpanded(),
										r.intractable() ? "yes" : "no", B2.bestindex()+1, B2.best().depthOfOpt()+1,
										B2.secondbest().depthOfPes()+1, bestPes));
							}
						} catch (IOException | RuntimeException e) {
							e.printStackTrace();
						}
					});
					recordProgress("Completed runs");
				});
			}
//			recordProgress((int) (seed - initial_seed));
			
			exec.shutdown();
			try {
				exec.awaitTermination(4, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (!exec.isTerminated()) exec.shutdownNow();
			bw.close();
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
		}
	}
	
	private static void getAlreadyComputedRuns(String previous_run_file, long[][] _skip_seeds, String[][][] _skip_algs) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(previous_run_file));
			String content = null;
			String[] data;
			HashMap<Long, ArrayList<String>> runs = new HashMap<>();
			while((content = br.readLine()) != null) {
				data = content.split(",");
				if (!data[6].matches("\\s*[\\d]+\\s*")) continue;
				long seed = Long.parseLong(data[6]);
				ArrayList<String> algs = runs.get(seed);
				if (algs == null) runs.put(seed, algs = new ArrayList<>());
				algs.add(data[1]);
			}
			long[] skip_seeds = _skip_seeds[0] = new long[runs.keySet().size()];
			String[][] skip_algs = _skip_algs[0] = new String[runs.keySet().size()][];
			int s_i = 0;
			for (Long seed : runs.keySet()) skip_seeds[s_i++] = seed;
			Arrays.sort(skip_seeds);
			for (int i=0; i < skip_seeds.length; i++) {
				ArrayList<String> algs = runs.get(skip_seeds[i]);
				skip_algs[i] = new String[algs.size()];
				int k_i = 0;
				for (String alg : algs) skip_algs[i][k_i++] = alg;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
