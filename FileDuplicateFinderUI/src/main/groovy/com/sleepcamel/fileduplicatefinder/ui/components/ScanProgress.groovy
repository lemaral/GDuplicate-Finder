package com.sleepcamel.fileduplicatefinder.ui.components

import groovy.beans.Bindable
import groovy.lang.Closure


import org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport
import org.eclipse.core.databinding.DataBindingContext
import org.eclipse.core.databinding.UpdateValueStrategy
import org.eclipse.core.databinding.beans.BeansObservables
import org.eclipse.core.databinding.observable.value.IObservableValue
import org.eclipse.jface.databinding.swt.WidgetProperties
import org.eclipse.swt.widgets.Display

import org.apache.commons.lang3.time.StopWatch
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.FileDialog
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.ProgressBar

import com.sleepcamel.fileduplicatefinder.core.domain.DuplicateFinder
import com.sleepcamel.fileduplicatefinder.core.domain.DuplicateFinderProgress
import com.sleepcamel.fileduplicatefinder.core.domain.filefilters.AndWrapperFilter
import com.sleepcamel.fileduplicatefinder.ui.adapters.ClosureSelectionAdapter
import com.sleepcamel.fileduplicatefinder.ui.adapters.NegativeUpdateValueStrategy
import com.sleepcamel.fileduplicatefinder.ui.utils.Utils

@Bindable
public class ScanProgress extends Composite {
	
	Label label
	ProgressBar progressBar
	
	Composite btnComposite
	Button btnSuspend
	Button btnResume
	Button btnSaveProgress
	
	Closure finishedFindingDuplicates
	Closure cancelFindingDuplicates
	
	boolean suspended = false
	
	def duplicateFinder
	DuplicateFinderProgress currentProgress

	public ScanProgress(Composite parent, int style) {
		super(parent,  SWT.FILL)
		setLayout(new GridLayout(1, false))
		
		progressBar = new ProgressBar(this, SWT.NONE)
		GridData gridData = new GridData(SWT.CENTER, SWT.BOTTOM, true, true)
		gridData.widthHint = 400
		progressBar.setLayoutData(gridData)
		
		Composite actionComposite = new Composite(this, SWT.CENTER)
		actionComposite.setLayout(new GridLayout(1, false))
		gridData = new GridData(SWT.FILL, SWT.TOP, true, false)
		actionComposite.setLayoutData(gridData)
		
		btnComposite = new Composite(actionComposite, SWT.NONE)
		btnComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1))
		RowLayout rl_btnComposite = new RowLayout(SWT.HORIZONTAL)
		rl_btnComposite.justify = true
		rl_btnComposite.spacing = 10
		btnComposite.setLayout(rl_btnComposite)
		
		btnSuspend = new Button(btnComposite, SWT.NONE)
		btnSuspend.setText("Suspend search")
		btnSuspend.addSelectionListener(new ClosureSelectionAdapter(c: suspend))
		
		btnResume = new Button(btnComposite, SWT.NONE)
		btnResume.setText("Resume search")
		btnResume.addSelectionListener(new ClosureSelectionAdapter(c: resume))
		
		btnSaveProgress = new Button(btnComposite, SWT.NONE)
		btnSaveProgress.setText("Save progress")
		btnSaveProgress.addSelectionListener(new ClosureSelectionAdapter(c: save))
		
		Button btnCancel = new Button(btnComposite, SWT.NONE)
		btnCancel.setText("Cancel search")
		btnCancel.addSelectionListener(new ClosureSelectionAdapter(c: cancel))
		
		label = new Label(actionComposite, SWT.CENTER | SWT.FILL)
		gridData = new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1)
		gridData.heightHint = 200
		gridData.widthHint = 400
		label.setLayoutData(gridData)
		label.setText("Progress")
		
		createBindings()
	}
	
	def createBindings(){
		def bindingContext = new DataBindingContext()
		
		IObservableValue modelSuspendValue = BeansObservables.observeValue(this, 'suspended')
		
		UpdateValueStrategy strategy = new NegativeUpdateValueStrategy()
		
		bindingContext.bindValue(WidgetProperties.enabled().observe(btnSuspend), modelSuspendValue, strategy, strategy)
		bindingContext.bindValue(WidgetProperties.enabled().observe(btnResume), modelSuspendValue)
		bindingContext.bindValue(WidgetProperties.enabled().observe(btnSaveProgress), modelSuspendValue)
	}
	
	def scanAndSearch(filters, directories){
		suspended = false
		def duplicateFinder = new DuplicateFinder(directories:directories)
		if ( !filters.isEmpty() ) duplicateFinder.filter = new AndWrapperFilter(filters : filters)

		Thread.start { thread ->

			Display.getDefault().syncExec(new Runnable() { public void run() {
			btnComposite.visible = false
			}})
			
			def progress = duplicateFinder.findProgress
			duplicateFinder.scan()
			def stopWatch = new StopWatch()
			stopWatch.start()

			while ( !progress.finishedScanning ){
				def elapsedTime = stopWatch.getTime()
				double currentFiles = progress.totalFiles
				def filesPerSec = currentFiles / (elapsedTime / 1000.0)
				Display.getDefault().syncExec(new Runnable() { public void run() {
					label.text = "Scanning...\nFiles found: ${currentFiles} - Size: ${Utils.formatBytes(progress.totalFileSize)} - Elapsed time: ${Utils.formatInterval(elapsedTime)} - Files Per Sec: $filesPerSec"
				}})
				Thread.sleep(1000L)
			}
			
			findDuplicates(progress)
			return
		}
	}
	
	def suspend = {
		duplicateFinder.suspend()
		reportingThread.interrupt()
		suspended = true
	}
	
	def resume = {
		suspended = false
		resumeSearch(currentProgress)
	}
	
	def reportingThread
	
	def findDuplicates(findProgress){
		search(findProgress, 'findDuplicates')
	}
	
	def resumeSearch(findProgress){
		search(findProgress, 'resume')
	}

	def search(findProgress, methodName){
		currentProgress = findProgress
		suspended = false
		reportingThread = Thread.start { thread ->
			try{
			duplicateFinder = new DuplicateFinder()
			duplicateFinder.findProgress = findProgress
			
			Display.getDefault().syncExec(new Runnable() { public void run() {
				btnComposite.visible = true
				def intPercentDone = Math.ceil(findProgress.percentDone() * 100) as Integer
				progressBar.setSelection(intPercentDone)
				progressBar.setMaximum(100)
			}})
			
			duplicateFinder.invokeMethod(methodName,null)
			def stopWatch = new StopWatch()
			stopWatch.start()

			while( !findProgress.finishedFindingDuplicates ){
				Thread.sleep(1000L)
				def percentDone = findProgress.percentDone()
				def elapsedTime = stopWatch.getTime()
				def timeLeft = Math.floor((elapsedTime * (1 - percentDone)) / percentDone) as Long
				def intPercentDone = Math.ceil(percentDone * 100) as Integer

				Display.getDefault().syncExec(new Runnable() { public void run() {
					progressBar.setSelection(intPercentDone)
					label.text = "Finding duplicates...\nProgress ${Utils.percentString(percentDone)}\n"+
											"Processed ${findProgress.processedFilesQty} of ${findProgress.totalFiles} files\n"+
											"Processed ${Utils.formatBytes(findProgress.processedFileSize)} of ${Utils.formatBytes(findProgress.totalFileSize)}\n"+
											"Elapsed time: ${Utils.formatInterval(elapsedTime)} - Remaining time: ${Utils.formatInterval(timeLeft)}"
				}})
			}
			
			Display.getDefault().asyncExec(new Runnable() {	public void run() {
				finishedFindingDuplicates.call(duplicateFinder.duplicatedEntries)
			}})
			}catch(Exception e){}
		}
	}
	
	def cancel = {
		if ( !suspended ){
			suspend()
			suspended = false
		}
		cancelFindingDuplicates.call(null)
	}
	
	def save = {
		FileDialog dlg = new FileDialog(shell, SWT.SAVE);
		dlg.setFilterNames(['Search progress session (*.sps)'] as String []);
		dlg.setFilterExtensions(['*.sps'] as String []);
		String fn = dlg.open();
		if (fn != null) {
			synchronized (currentProgress) {
				new File(fn).withObjectOutputStream { oos ->
					oos.writeObject(currentProgress)
					DefaultGroovyMethodsSupport.closeQuietly(oos)
				}
			}
		}
	}
}