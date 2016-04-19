package optimizer.solver.genetic_algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Stack;
import java.util.Map.Entry;

import optimizer.Problem;
import optimizer.domain.Element;
import optimizer.domain.Task;

public class Chromosome implements Comparable<Chromosome>, Cloneable {
	private boolean[] genes;
	private int numBitsTaskID;
	private int score;
	private Problem problem;
	public Chromosome(Problem problem) {
		this.problem = problem;
		this.numBitsTaskID = minNumBits(problem.getTasks().size());
		this.genes = new boolean[calculateChromosomeSize()];
		randomizeGenes();
		evaluate();
	}

	public Chromosome(Problem problem, boolean[] genes) {
		this.problem = problem;
		this.numBitsTaskID = minNumBits(problem.getTasks().size());
		this.genes = genes;
		evaluate();
	}

	private static int minNumBits(int size) {
		if (size <= 1) return size;
		return (int) Math.floor(Math.log(size - 1)/Math.log(2) + 1);
	}

	public int calculateChromosomeSize() {
		return problem.getTasks().size() * (this.numBitsTaskID + problem.getElements().size());
	}

	public boolean[] getGenes() {
		return genes;
	}

	public void flipGene(int index) {
		this.genes[index] = !this.genes[index];
	}

	public int evaluate() {
		List<Integer> taskOrder = new ArrayList<Integer>();
		List<List<Integer>> taskElements = new ArrayList<List<Integer>>();
		List<Task> tasks = problem.getTasks();
		int bitsPerTask = this.numBitsTaskID + problem.getElements().size();
		for (int i = 0; i < tasks.size(); i++) {
			int offset = i * bitsPerTask;
			boolean[] bTaskID = Arrays.copyOfRange(this.genes, offset, offset + this.numBitsTaskID);
			int taskID = booleanArrToInt(bTaskID);
			if (taskID >= problem.getTasks().size()) continue; // Ignore, because ID is invalid
			if (taskOrder.contains(taskID)) continue; // Ignore, because the task is already in the list
			taskOrder.add(taskID);
			taskElements.add(readElements(offset));
		}

		// Add missing tasks ordered by ID
		for (int i = 0; i < tasks.size(); i++) {
			if (!taskOrder.contains(i)) {
				taskOrder.add(i);
				taskElements.add(readElements(i * bitsPerTask));
			}
		}
		
		HashMap<Task, Integer> taskCompletionTimes = new LinkedHashMap<Task, Integer>();
		HashMap<Element, Integer> elementReadyTimes = new LinkedHashMap<Element, Integer>();
		createTimes(taskCompletionTimes, elementReadyTimes);
		int currTime = 0;
		
		while (!allTasksDone(taskCompletionTimes)) {
			for (int i = 0; i < taskOrder.size(); i++) {
				Task task = problem.getTasks().get(i);
				if (!taskCompletionTimes.get(task).equals(Integer.MAX_VALUE)) continue; // Task already done, skip it
				int taskStartTime = calculateTaskStartTime(task, taskCompletionTimes, elementReadyTimes);
				if (taskStartTime == Integer.MAX_VALUE) continue; // Precedences not ready, try a different task
				currTime = taskStartTime;
				allocateElementsToTask(task, taskElements.get(i), currTime, taskCompletionTimes, elementReadyTimes);
				break;
			}
		}

		score = findMaxTaskCompletionTime(taskCompletionTimes);
		return score;
	}

	private List<Integer> readElements(int offset) {
		ArrayList<Integer> elements = new ArrayList<Integer>();
		int fullOffset = offset + this.numBitsTaskID;
		int limit = fullOffset + problem.getElements().size();
		for (int j = fullOffset; j < limit; j++) {
			if (this.genes[j])
				elements.add(j - fullOffset);
		}
		return elements;
	}

	private int booleanArrToInt(boolean[] booleans) {
		int n = 0;
		for (boolean b : booleans)
			n = (n << 1) | (b ? 1 : 0);
		return n;
	}
	
	private boolean[] intToBooleanArr(int i, int numBits) {
		boolean[] b = new boolean[numBits];
		for (int j = b.length - 1; j >= 0; j--) {
	        b[j] = (j & (1 << j)) != 0;
	    }
		return b;
	}

	private void randomizeGenes() {
		Random r = new Random();
		List<Integer> topoSort = randomTopoSort();
		int taskSize = this.numBitsTaskID + problem.getElements().size();
		for (int i = 0; i < topoSort.size(); i++) {
			int n = topoSort.get(i);
			boolean[] taskID = intToBooleanArr(n, this.numBitsTaskID);
			for (int j = 0; j < this.numBitsTaskID; j++) {
				genes[i * taskSize + j] = taskID[j];
			}
			for (int j = this.numBitsTaskID; j < taskSize; j++) {
				genes[i * taskSize + j] = r.nextBoolean();
			}
		}
	}

	private List<Integer> randomTopoSort() {
		LinkedList<Integer> result = new LinkedList<Integer>();
		List<Task> tasks = problem.getTasks();

		LinkedList<Integer> sources = new LinkedList<Integer>(); 
		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).getPrecedences().size() == 0) {
				sources.add(i);
			}
		}
		int[] inDegree = new int[tasks.size()];
		for (int i = 0; i < tasks.size(); i++) {
			inDegree[i] = tasks.get(i).getPrecedences().size();
		}
		while( !sources.isEmpty() ) {
			Collections.shuffle(sources);
			int n = sources.poll();
			result.add(n);

			for(int i = 0; i < tasks.size(); i++) {
				if (!tasks.get(i).getPrecedences().contains(tasks.get(n)))
					continue;
				inDegree[i]--;
				if(inDegree[i] == 0) sources.add(i);
			}
		}

		return result;
	}

	public int getFitness() {
		return score;
	}

	public int getSize() {
		return this.genes.length;
	}

	@Override
	public int compareTo(Chromosome c) {
		return c.score - this.score;
	}

	private int findMaxTaskCompletionTime(HashMap<Task, Integer> taskCompletionTimes) {
		int max = 0;
		Iterator<Entry<Task, Integer>> it = taskCompletionTimes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Task, Integer> pair = (Map.Entry<Task, Integer>)it.next();
			int value = pair.getValue();
			if (value > max)
				max = value;
		}
		return max;
	}

	private void allocateElementsToTask(Task task, List<Integer> taskElements, int currTime, HashMap<Task, Integer> taskCompletionTimes, HashMap<Element, Integer> elementReadyTimes) {
		float totalPerformance = 0;
		ArrayList<Element> assignedElements = new ArrayList<Element>();
		for (int id : taskElements) {
			if (currTime < elementReadyTimes.get(problem.getElements().get(id)))
				break; // Element not ready, don't assign to task
			float performance = problem.getElements().get(id).getSkillPerfomance(task.getSkill());
			if (performance <= 0)
				break; // Element hasn't got the skill to do the task, don't assign
			totalPerformance += performance;
			assignedElements.add(problem.getElements().get(id));
		}
		if (totalPerformance <= 0) { // Can happen if the algorithm doesn't generate an element with the skill to realize the task. If so, find any available element to be assigned to the task.
			float performance = findFreeElementID(task, elementReadyTimes).getSkillPerfomance(task.getSkill());
			totalPerformance += performance;
		}
		int duration = (int)(totalPerformance * task.getDuration());
		duration = (int)(task.getDuration() / totalPerformance);
		int endTime = currTime + duration;
		taskCompletionTimes.put(task, endTime);
		for (Element element : assignedElements) {
			elementReadyTimes.put(element, endTime);
		}
	}

	private Element findFreeElementID(Task task, HashMap<Element, Integer> elementReadyTimes) {
		Iterator<Entry<Element, Integer>> it = elementReadyTimes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Element, Integer> pair = (Map.Entry<Element, Integer>)it.next();
			if (pair.getKey().getSkillPerfomance(task.getSkill()) > 0)
				return pair.getKey();
		}
		return null;
	}

	private int calculateTaskStartTime(Task task, HashMap<Task, Integer> taskCompletionTimes, HashMap<Element, Integer> elementReadyTimes) {
		int precedencesReadyTime = 0;
		if (task.getPrecedences().size() >= 1)
		{
			for (Task precedence : task.getPrecedences()) {
				int time = taskCompletionTimes.get(precedence);
				if (time > precedencesReadyTime)
					precedencesReadyTime = time;
			}
		}
		if (precedencesReadyTime == Integer.MAX_VALUE)
			return precedencesReadyTime; // If the task's precedences haven't been met, there's not need to check if there are elements available

		int elementReadyTime = Integer.MAX_VALUE;
		for (Entry<Element, Integer> entry : elementReadyTimes.entrySet()) {
			if (entry.getKey().getSkillPerfomance(task.getSkill()) > 0) {
				int time = entry.getValue();
				elementReadyTime = Math.min(elementReadyTime, time);
			}
		}
		return Math.max(precedencesReadyTime, elementReadyTime);
	}

	private boolean allTasksDone(HashMap<Task, Integer> taskCompletionTimes) {
		Iterator<Entry<Task, Integer>> it = taskCompletionTimes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Task, Integer> pair = (Map.Entry<Task, Integer>)it.next();
			if (pair.getValue().equals(Integer.MAX_VALUE))
				return false;
		}
		return true;
	}

	private void createTimes(HashMap<Task, Integer> taskCompletionTimes, HashMap<Element, Integer> elementReadyTimes) {
		for (Task task : problem.getTasks()) {
			taskCompletionTimes.put(task, Integer.MAX_VALUE);
		}
		for (Element element : problem.getElements()) {
			elementReadyTimes.put(element, 0);
		}
	}

	private boolean precedencesReady(Task task, int currTime, HashMap<Task, Integer> taskCompletionTimes) {
		List<Task> precedences = task.getPrecedences();
		for (Task precedence : precedences) {
			if (currTime < taskCompletionTimes.get(precedence)) {
				return false;
			}
		}
		return true;
	}

	public Chromosome(Chromosome c) {
		boolean[] newGenes = c.genes.clone();
		problem = c.problem;
		genes = newGenes;
		score = c.score;
	}

	@Override
	public Chromosome clone() {
		return new Chromosome(this);
	}
}
