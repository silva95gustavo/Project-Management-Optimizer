package solver.genetic_algorithm;

import java.util.Random;

import optimizer.Problem;

public class Chromosome implements Comparable<Chromosome>, Cloneable {
	private boolean[] genes;
	private int fitness;

	public Chromosome(int length) {
		this.genes = new boolean[length];
		randomizeGenes();
	}

	public Chromosome(boolean[] genes) {
		this.genes = genes;
	}

	public boolean[] getGenes() {
		return genes;
	}

	public void flipGene(int index) {
		this.genes[index] = !this.genes[index];
	}

	public int evaluate(Problem problem) {
		// TODO
		return 0;
	}

	private void randomizeGenes() {
		Random r = new Random();
		for (int i = 0; i < genes.length; i++) {
			genes[i] = r.nextBoolean();
		}
	}

	public int getFitness() {
		return this.fitness;
	}

	@Override
	public int compareTo(Chromosome c) {
		return this.fitness - c.fitness;
	}
	
	@Override
	public Object clone() {
		boolean[] newGenes = this.genes.clone();
		Chromosome c = new Chromosome(newGenes);
		return c;
	}
}
