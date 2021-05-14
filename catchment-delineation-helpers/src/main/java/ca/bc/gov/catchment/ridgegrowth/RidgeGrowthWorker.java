package ca.bc.gov.catchment.ridgegrowth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.locationtech.jts.geom.LineString;

public class RidgeGrowthWorker extends Thread {
	
	private static int NEXT_ID = 1;
	
	private BlockingQueue<RidgeGrowthTask> tasks;
	private RidgeGrowthStrategy strategy;
	private List<RidgeGrowthListener> listeners;
	private int id;
	private boolean finishRequested;
	private boolean isFinished;
	
	public RidgeGrowthWorker(BlockingQueue<RidgeGrowthTask> tasks, RidgeGrowthStrategy strategy) {
		this.tasks = tasks;
		this.strategy = strategy;
		this.listeners = new ArrayList<RidgeGrowthListener>();
		this.id = NEXT_ID++;
		this.finishRequested = false;
		this.isFinished = false;
		
		System.out.println("created worker "+this.id);
	}
	

	public void run() {
		RidgeGrowthTask task = null;
		try {			
			while(true) {
				if (this.finishRequested && tasks.size() == 0) {
					break;
				}
				//wait up to 5 seconds for a task to be available
				task = tasks.poll(5000, TimeUnit.MILLISECONDS);
				if (task != null) {
					System.out.println("worker "+this.id+" started task");
					try {
						LineString ridge = strategy.growRidge(task);	
						this.notifyListenersSuccess(task, ridge);
					}
					catch (IOException e) {
						this.notifyListenersError(task);
					}
				}
				
			}
		} 
		catch (InterruptedException e) {
			//do nothing
		}	
		this.finish();
		
	}
	
	public void addListener(RidgeGrowthListener listener) {
		this.listeners.add(listener);
	}
	
	public void notifyListenersSuccess(RidgeGrowthTask task, LineString ridge) {
		for(RidgeGrowthListener listener : listeners) {
			listener.onRidgeSuccess(task, ridge);
		}
	}
	
	public void notifyListenersError(RidgeGrowthTask task) {
		for(RidgeGrowthListener listener : listeners) {
			listener.onRidgeError(task);
		}
	}
	
	public void notifyListenersFinished() {
		for(RidgeGrowthListener listener : listeners) {
			listener.onFinished();
		}
	}
	
	public boolean isFinished() {
		return this.isFinished;
	}
	
	private void finish() {
		this.isFinished = true;
		this.notifyListenersFinished();
	}
	
	public void requestFinishWhenQueueEmpty() {
		this.finishRequested = true;
	}
}
