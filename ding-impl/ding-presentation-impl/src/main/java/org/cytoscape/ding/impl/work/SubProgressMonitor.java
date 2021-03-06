package org.cytoscape.ding.impl.work;

public class SubProgressMonitor implements ProgressMonitor {

	private final ProgressMonitor wrapped;
	private final double percentage; // eg 0.2 = 20 percent
	private double currentProgress;
	
	SubProgressMonitor(ProgressMonitor wrapped, double percentage) {
		this.wrapped = wrapped;
		this.percentage = percentage;
	}
	
	@Override
	public boolean isCancelled() {
		return wrapped.isCancelled();
	}
	
	@Override
	public void cancel() {
	}

	@Override
	public void addProgress(double progress) {
		currentProgress += progress;
		wrapped.addProgress(progress * percentage);
	}

	@Override
	public void done() {
		addProgress(1.0 - currentProgress);
	}

}
