package org.springaicommunity.github.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Decorator that adds adaptive time-window splitting to any collection service.
 *
 * <p>
 * The GitHub Search API returns a maximum of 1,000 results per query. When collecting
 * from large repositories over a wide date range, this limit is easily exceeded. This
 * decorator transparently splits the collection into smaller time windows, delegating
 * each window to the wrapped service, and merges the results.
 *
 * <p>
 * When both {@code createdAfter} and {@code createdBefore} are set on the request, this
 * service uses {@link AdaptiveWindowPlanner} to determine the optimal split. When date
 * range is not set, the request passes through to the delegate unchanged.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * IssueCollectionService issueService = builder.buildIssueCollector();
 * var windowed = new WindowedCollectionService<>(issueService, planner,
 *     (after, before) -> graphQLService.getSearchIssueCount(
 *         buildQueryWithDates(request, after, before)));
 * windowed.collectItems(request);
 * }</pre>
 *
 * @param <T> the type of items being collected (e.g., Issue, AnalyzedPullRequest)
 */
public class WindowedCollectionService<T> {

	private static final Logger logger = LoggerFactory.getLogger(WindowedCollectionService.class);

	private final BaseCollectionService<T> delegate;

	private final AdaptiveWindowPlanner planner;

	private final BiFunction<String, String, Integer> countFunction;

	/**
	 * Create a windowed collection service.
	 * @param delegate the underlying collection service to delegate each window to
	 * @param planner the adaptive window planner for determining time window splits
	 * @param countFunction function that takes (createdAfter, createdBefore) and returns
	 * the number of items matching the base request criteria in that date range. Should
	 * return -1 on error.
	 */
	public WindowedCollectionService(BaseCollectionService<T> delegate, AdaptiveWindowPlanner planner,
			BiFunction<String, String, Integer> countFunction) {
		this.delegate = delegate;
		this.planner = planner;
		this.countFunction = countFunction;
	}

	/**
	 * Collect items, automatically splitting into time windows if needed.
	 *
	 * <p>
	 * If the request has both createdAfter and createdBefore set, the planner checks
	 * whether the total count exceeds the Search API limit and splits accordingly. If no
	 * date range is set, or only one window is needed, the request passes through
	 * directly.
	 * @param request the collection request
	 * @return merged collection result across all windows
	 */
	public CollectionResult collectItems(CollectionRequest request) {
		if (request.createdAfter() == null || request.createdBefore() == null) {
			logger.info("No date range set, passing through to delegate");
			return delegate.collectItems(request);
		}

		logger.info("Planning time windows for {} to {}...", request.createdAfter(), request.createdBefore());

		List<AdaptiveWindowPlanner.TimeWindow> windows = planner.planWindows(request.createdAfter(),
				request.createdBefore(), countFunction);

		if (windows.size() <= 1) {
			logger.info("Single window sufficient, passing through to delegate");
			return delegate.collectItems(request);
		}

		logger.info("Split into {} time windows", windows.size());

		int totalItems = 0;
		int totalProcessed = 0;
		List<String> allBatchFiles = new ArrayList<>();
		String outputDirectory = null;
		int batchOffset = 0;

		for (int i = 0; i < windows.size(); i++) {
			AdaptiveWindowPlanner.TimeWindow window = windows.get(i);

			logger.info("Window {}/{}: {} to {}", i + 1, windows.size(), window.createdAfter(), window.createdBefore());

			CollectionRequest windowRequest = request.toBuilder()
				.createdAfter(window.createdAfter())
				.createdBefore(window.createdBefore())
				.batchOffset(batchOffset > 0 ? batchOffset : null)
				.clean(i == 0 && request.clean())
				.build();

			CollectionResult windowResult = delegate.collectItems(windowRequest);

			totalItems += windowResult.totalIssues();
			totalProcessed += windowResult.processedIssues();
			allBatchFiles.addAll(windowResult.batchFiles());

			if (outputDirectory == null) {
				outputDirectory = windowResult.outputDirectory();
			}

			batchOffset += windowResult.batchFiles().size();

			logger.info("Window {}/{} complete: {}/{} items, {} batches", i + 1, windows.size(),
					windowResult.processedIssues(), windowResult.totalIssues(), windowResult.batchFiles().size());
		}

		logger.info("Windowed collection complete: {}/{} total items across {} windows, {} batches", totalProcessed,
				totalItems, windows.size(), allBatchFiles.size());

		return new CollectionResult(totalItems, totalProcessed, outputDirectory != null ? outputDirectory : "",
				allBatchFiles);
	}

}
