package edu.dlf.refactoring.study;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.eclipse.jdt.core.IJavaElement;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import edu.dlf.refactoring.analyzers.FJUtils;
import edu.dlf.refactoring.analyzers.JavaModelAnalyzer;
import edu.dlf.refactoring.design.DesignUtils;
import edu.dlf.refactoring.design.IFactorComponent;
import edu.dlf.refactoring.design.JavaElementPair;
import edu.dlf.refactoring.design.ServiceLocator.ChangeCompAnnotation;
import edu.dlf.refactoring.utils.DlfExecutorService;
import fj.Effect;
import fj.Equal;
import fj.F;
import fj.Ord;
import fj.P2;
import fj.Unit;
import fj.data.List;

@Singleton
public class CompareProjectsInWorkspaceStudy extends AbstractStudy{

	private final IFactorComponent changeComp;
	private final Logger logger;	
	private final List<Integer> pairStarts = List.range(0, 10).map(multiply);
	private final ExecutorService queue;
	private final LoadingCache cache;
	
	private static final int stepLength = 10;
	private int index = 0;
	
	@Inject
	public CompareProjectsInWorkspaceStudy(
			Logger logger,
			ExecutorService queue,
			LoadingCache cache,
			@ChangeCompAnnotation IFactorComponent changeComp) {
		super("Compare projects in work space");
		this.changeComp = changeComp;
		this.logger = logger;
		this.queue = queue;
		this.cache = cache;
	}
	
	private static final F<Integer, Integer> multiply = new F<Integer,
			Integer>() {
		@Override
		public Integer f(Integer num) {
			return num * stepLength;
	}};		
	
	private final F<IJavaElement, String> getOriginalName = 
		new F<IJavaElement, String>() {
		@Override
		public String f(IJavaElement element) {
			return element.getElementName().replaceAll("\\d*$", "");
	}};
	
	private final Equal<IJavaElement> projectEq = Equal.stringEqual.comap
		(getOriginalName);
	
	private final Ord<IJavaElement> projectOrd = Ord.stringOrd.comap
		(getOriginalName);

	private final Ord<IJavaElement> projectNumberOrd = Ord.intOrd.comap(
		new F<IJavaElement, Integer>() {
			@Override
			public Integer f(IJavaElement project) {
				return getContainedInteger(project.getElementName());
	}});
	
	private final Effect<P2<IJavaElement, IJavaElement>> printProjectPair =
		new Effect<P2<IJavaElement,IJavaElement>>() {
			@Override
			public void e(P2<IJavaElement, IJavaElement> p) {
				logger.info("Project before: " + p._1().getElementName());
				logger.info("Project after: " + p._2().getElementName());		
	}};
	
	private final Effect<P2<IJavaElement, IJavaElement>> handlePair = 
		new Effect<P2<IJavaElement, IJavaElement>>() {
		@Override
		public void e(P2<IJavaElement, IJavaElement> pair) {
			printProjectPair.e(pair);
			F<JavaElementPair, Object> converter = FJUtils.getTypeConverter
				((JavaElementPair)null, (Object)null);
			F<Object, Unit> feeder = DesignUtils.feedComponent.flip().
				f(changeComp);
			DesignUtils.convertProduct2JavaElementPair.andThen(converter).
				andThen(feeder).f(pair);
			try {
				queue.shutdown();
				queue.awaitTermination(1, TimeUnit.DAYS);
			} catch (Exception e) {
				logger.fatal(e);
			}
			cache.cleanUp();
			((DlfExecutorService)queue).restart();
	}};
	
	private final F<List<IJavaElement>, List<P2<IJavaElement, IJavaElement>>> 
		pairProjects = new F<List<IJavaElement>, List<P2<IJavaElement, 
			IJavaElement>>>() {
		@Override
		public List<P2<IJavaElement, IJavaElement>> f(List<IJavaElement> 
				projects) {
			projects = projects.sort(projectNumberOrd).reverse();
			F<Object, Unit> feeder = DesignUtils.feedComponent.flip().
				f(changeComp);
			F<JavaElementPair, Object> converter = FJUtils.getTypeConverter
				((JavaElementPair)null, (Object)null);
			List<P2<IJavaElement, IJavaElement>> projectPairs = projects.
				zip(projects.drop(1));
			return projectPairs;
	}};
		
	@Override
	protected void study() {
		final List<IJavaElement> projects = JavaModelAnalyzer.
			getJavaProjectsInWorkSpace();
		final List<List<IJavaElement>> groups = projects.group(projectEq);
		final List<P2<IJavaElement, IJavaElement>> allPairs = groups.bind
			(pairProjects);
		allPairs.foreach(handlePair);
		return;/*
		int start = this.pairStarts.index(index);
		int end = this.pairStarts.index(index + 1) - 1;
		index += 1;
		FJUtils.getSubList(allPairs, start, end).foreach(handlePair);*/
	}

}
