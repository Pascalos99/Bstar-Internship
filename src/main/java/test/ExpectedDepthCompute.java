package test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.ejml.simple.SimpleMatrix;

public class ExpectedDepthCompute {

	public static void main(String[] args) {
		boolean berliner = false;
		int width = 2;
		int depth = 4;
		
		ExpectedDepthCompute edc = new ExpectedDepthCompute(width, berliner);
		edc.computeTs(depth);
		
		// PRINT transition probabilities
		for (int i=0; i < edc.t.length; i++) {
			System.out.format("s%02d -> ", i);
			double sum = 0.;
			for (int j=0; j < edc.t[i].length; j++) {
				System.out.format("%.5f ", edc.t[i][j]);
				sum += edc.t[i][j];
			} System.out.format("|| %.3f\n", sum);
		}
		
		// COMPUTE steady state estimate
		double[] dataA = new double[depth*depth];
		double[] dataB = new double[depth];
		for (int i=1; i <= depth; i++) {
			for (int j=1; j <= depth; j++) {
				double a = edc.t[i][j] - (i==j ? 1 : 0);
				if (j >= depth) {
					for (int k=depth+1; k <= depth * width; k++) {
						a += edc.t[i][k];
					}
				}
				dataA[(i-1)*depth+j-1] = a;
			}
			dataB[i-1] = -1. - edc.t[i][0];
		}
		System.out.println();
		SimpleMatrix A = new SimpleMatrix(depth, depth, true, dataA);
		SimpleMatrix b = new SimpleMatrix(depth, 1, true, dataB);
		A.print("%.4f");
		b.print("%.4f");
		SimpleMatrix x = A.solve(b);
		x.print("%.4f");
	}
	
	final int width, range;
	final boolean berliner;
	private double[][] t;
	
	public ExpectedDepthCompute(int width, boolean berliner) {
		this.width = width;
		this.range = 2;
		this.berliner = berliner;
	}
	
	public void computeTs(int max_state) {
		t = new double[max_state + 1][width * max_state + 1];
		if (max_state >= 1) {
			for (int j=0; j < t[1].length; j++)
				t[1][j] = computeT0(j);
			
			ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			
			for (int i=2; i < t.length; i++)
				for (int j=0; j < t[i].length && j <= width*i; j++) {
					final int I = i, J = j;
					exec.execute(() -> { t[I][J] = computeT(I, J); });
				}
			exec.shutdown();
			try {
				exec.awaitTermination(5, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private double computeT0(int j) {
		double result = ((double)combinations(width, j)) / Math.pow(2, width);
		if (berliner) {
			if (j==0) result -= 2. / Math.pow(4, width);
			else if (j==1) result += 2. / Math.pow(4, width);
		}
		return result;
		
	}
	
	private double computeT(int i, int j) {
		if (i > 5)
			return IntStream.range(0, Math.min(j, width)+1).mapToDouble(k -> {
				double[] result = { 0. };
				int[] partition = new int[i];
				partition[0] = k;
				resursiveComputeT(j - k, i, width, partition, 1, result);
				return result[0];
			}).parallel().sum();
		
		double[] result = { 0. };
		resursiveComputeT(j, i, width, new int[i], 0, result);
		return result[0];
	}
	
	private void resursiveComputeT(int N, int maxLength, int maxElement, int[] partition, int index, double[] result) {
		if (N < 0) return;
		if (N == 0) {
			double product = 1.;
			for (int k=0; k < maxLength; k++) product *= t[1][partition[k]];
			result[0] += product;
			return;
		}
		if (index >= maxLength) return;
		for (int k = Math.min(N, maxElement); k >= 0; k--) {
            partition[index] = k;
            resursiveComputeT(N - k, maxLength, maxElement, partition, index + 1, result);
        }
	}
	
	public static long combinations(int n, int k) {
        long result = 1l;
        for (int i = 0; i < k; i++) result = result * (n-i) / (i+1);
		return result;
	}
	
}
