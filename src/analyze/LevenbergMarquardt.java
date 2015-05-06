package analyze;

/**
 * 
 * 
 * @author C.M. Punter
 *
 */
public abstract class LevenbergMarquardt {

	public static int maxIterations = 100;
	public int iterations;
	public double sumOfSquares = 0;
	public double precision = 1e-6;
	public double rSquared = 0;
	
	public void solve(double[][] x, double[] y, double[] s, int n, double[] parameters, boolean[] vary, double[] error, double lambda) {
		
		// determine the number of parameters that can vary
		int numberOfParameters = 0;
		
		for (int i = 0; i < parameters.length; i++) {
			if (vary == null || vary[i])
				numberOfParameters++;
		}
		
		double[][] alpha = new double[numberOfParameters][numberOfParameters];
		double[][] beta = new double[numberOfParameters][1];
		double[] dyda = new double[parameters.length];
		double[] newParameters = new double[parameters.length];
		
		double sumOfSquaresBefore = 0;
		double sumOfSquaresAfter = 0;
		
		for (iterations = 1; iterations <= maxIterations; iterations++) {
			
			// initialize matrices
			for (int i = 0; i < numberOfParameters; i++) {
				for (int j = 0; j < numberOfParameters; j++)
					alpha[i][j] = 0;
				beta[i][0] = 0;
			}
			
			// determine initial sum of squares
			sumOfSquaresBefore = 0;
			
			for (int i = 0; i < n; i++) {
				
				double residual = (y[i] - getValue(x[i], parameters));
				
				if (s != null && s[i] != 0)
					residual /= s[i];
				
				sumOfSquaresBefore += residual * residual;
				
				getGradient(x[i], parameters, dyda);
				
				for (int j = 0, k = 0; j < parameters.length; j++) {
					if (vary == null || vary[j])
						dyda[k++] = dyda[j];
				}
				
				for (int j = 0; j < numberOfParameters; j++) {
					
					if (s != null && s[i] != 0)
						dyda[j] /= s[i];
					
					for (int k = 0; k <= j; k++)
						alpha[j][k] += dyda[j] * dyda[k];
					
					beta[j][0] += dyda[j] * residual;
				}
				
			}
			
			// fill in symmetric side
			for (int i = 0; i < numberOfParameters; i++) {
				for (int j = i + 1; j < numberOfParameters; j++)
					alpha[i][j] = alpha[j][i];
			}
			
			// include damping factor
			for (int i = 0; i < numberOfParameters; i++)
				alpha[i][i] *= 1 + lambda;
			
			gaussJordan(alpha, beta);
			
			// determine new sum of squares
			sumOfSquaresAfter = 0;
			
			for (int i = 0, j = 0; i < parameters.length; i++) {
				
				newParameters[i] = parameters[i];
				
				if (vary == null || vary[i])
					newParameters[i] += beta[j++][0];
			}
			
			for (int i = 0; i < n; i++) {
				double residual = (y[i] - getValue(x[i], newParameters));
				
				if (s != null && s[i] != 0)
					residual /= s[i];
				
				sumOfSquaresAfter += residual * residual;
			}
			
			double improvement = Math.abs(sumOfSquaresAfter - sumOfSquaresBefore);
			
			if (sumOfSquaresAfter < sumOfSquaresBefore) {
				
				for (int i = 0; i < parameters.length; i++)
					parameters[i] = newParameters[i];
				
				lambda /= 10;
			}
			else {
				sumOfSquaresAfter = sumOfSquaresBefore;
				lambda *= 10;
			}
			
			if (improvement < precision)	// stop condition
				break;
		}
		
		double[][] covar = new double[numberOfParameters][numberOfParameters];
		
		// initialize alpha matrix
		for (int i = 0; i < numberOfParameters; i++) {
			for (int j = 0; j < numberOfParameters; j++)
				alpha[i][j] = 0;
			
			covar[i][i] = 1;	// covar matrix is set to identity matrix
		}
		
		// determine alpha
		for (int i = 0; i < n; i++) {
			
			getGradient(x[i], parameters, dyda);
			
			for (int j = 0, k = 0; j < parameters.length; j++) {
				if (vary == null || vary[j])
					dyda[k++] = dyda[j];
			}
			
			for (int j = 0; j < numberOfParameters; j++) {
				
				if (s != null && s[i] != 0)
					dyda[j] /= s[i];
				
				for (int k = 0; k <= j; k++)
					alpha[j][k] += dyda[j] * dyda[k];
			}
			
		}
		
		// fill in symmetric side
		for (int i = 0; i < numberOfParameters; i++) {
			for (int j = i + 1; j < numberOfParameters; j++)
				alpha[i][j] = alpha[j][i];
		}
		
		// invert alpha
		gaussJordan(alpha, covar);

		for (int i = 0, j = 0; i < parameters.length; i++) {
			if (vary == null || vary[i])
				error[i] = Math.sqrt(covar[j][j++] * sumOfSquaresAfter / (n - numberOfParameters));
			else
				error[i] = 0;
		}
		
		// calculating R^2
		double mean = 0;
		double w = 0;
		
		for (int i = 0; i < n; i++) {
			
			double ssq = s == null ? 1 : s[i] * s[i];
			mean += y[i] / ssq;
			w += 1 / ssq;
		}
		
		mean /= w;
		
		double sst = 0;
		for (int i = 0; i < n; i++) {
			double ssq = s == null ? 1 : s[i] * s[i];
			double deviation = y[i] - mean;
			sst += (deviation * deviation) / ssq;
		}
		
		rSquared = 1 - (sumOfSquaresAfter / (n - numberOfParameters)) / (sst / (n - 1));
		sumOfSquares = sumOfSquaresAfter;
	}
	
	public void gaussJordan(double[][] left, double[][] right) {
		int n = left.length;
		int rCols = right[0].length;
		
		for (int i = 0; i < n; i++) {
			
			// find pivot
			int max = i;
			
			for (int j = i + 1; j < n; j++) {
				if (Math.abs(left[j][i]) > Math.abs(left[max][i]))
					max = j;
			}
			
			// swap rows
			double[] t = left[i];
			left[i] = left[max];
			left[max] = t;
			
			t = right[i];
			right[i] = right[max];
			right[max] = t;
			
			// reduce
			for (int j = 0; j < n; j++) {
				
				if (j != i) {
					double d = left[j][i] / left[i][i];
					
					left[j][i] = 0;
					
					for (int k = i + 1; k < n; k++)
						left[j][k] -= d * left[i][k];
					
					for (int k = 0; k < rCols; k++)
						right[j][k] -= d * right[i][k];
				}
			}
		}
		
		for (int i = 0; i < n; i++) {
			double d = left[i][i];
			
			for (int k = 0; k < rCols; k++)
				right[i][k] /= d;
			
			left[i][i] = 1;
		}
	}
	
	public abstract double getValue(double[] x, double[] parameters);
	public abstract void getGradient(double[] x, double[] parameters, double[] dyda);
	
	public static void main(String[] args) {
		
		double[][] x = {{0}, {1}, {2}, {3}, {4}, {5}, {6},
				{7}, {8}, {9}, {10}, {11}, {12}, {13}};
		
		double y[] = {3365.333251953, 3206.923095703, 3215.769287109,
				3474.846191406, 4320.333496094, 5953.307617188,
				7291.846191406, 7010.307617188, 5404.307617188,
				4016.153808594, 3668.281982422, 3543.769287109,
				3320.820556641, 3248.000000000};
		
		double[] s = {100, 200, 150, 300, 100, 100, 50, 105, 102, 1012, 12, 123, 122, 100};
		
		LevenbergMarquardt lm = new LevenbergMarquardt() {
			
			@Override
			public double getValue(double[] x, double[] p) {
				return p[0] + p[1] * Math.exp(-0.5 * ((x[0] - p[2]) / p[3]) * ((x[0] - p[2]) / p[3]));
			}
			
			@Override
			public void getGradient(double[] x, double[] p, double[] dyda) {
				double d = -p[2] + x[0];
				
				dyda[0] = 1.0;
				dyda[1] = Math.exp(-((d * d) / (2 * p[3] * p[3])));
				dyda[2] = (p[1] * dyda[1] * d) / (p[3] * p[3]);
				dyda[3] = (p[1] * dyda[1] * d * d) / (p[3] * p[3] * p[3]);
				
				
				dyda[1] = Math.exp(-(0.5 * (-p[2] + x[0]) * (-p[2] + x[0])) / (p[3] * p[3]));
				dyda[2] = (p[1] * dyda[1] * (-p[2] + x[0])) / (p[3] * p[3]);
				dyda[3] = (p[1] * dyda[1] * (-p[2] + x[0]) * (-p[2] + x[0])) / (p[3] * p[3] * p[3]);
			}
			
		};
		
		double[] p = {1000, 1000, 6, 1};
		double[] e = new double[p.length];
		boolean[] vary = new boolean[]{true, true, true, true};
		
		long beginTime = System.nanoTime();
		lm.solve(x, y, s, x.length, p, vary, e, 0.001);
		long endTime = System.nanoTime();
		
		System.out.printf("ellapsed time : %dns\n", (endTime - beginTime));
		
		for (int i = 0; i < p.length; i++)
			System.out.printf("%f, %f\n", p[i],  e[i]);
		
		System.out.printf("chi^2 %f\n", lm.sumOfSquares);
		System.out.printf("iterations %d\n", lm.iterations);
		System.out.printf("R^2 %f\n",  lm.rSquared);
		

	}

}
