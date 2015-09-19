package org.ctrip.ops.sysdev;

import org.ctrip.ops.sysdev.filters.BaseFilter;
import org.ctrip.ops.sysdev.inputs.BaseInput;
import org.ctrip.ops.sysdev.outputs.BaseOutput;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.log4j.Logger;
import org.ctrip.ops.sysdev.configs.HangoutConfig;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());

	public static void main(String[] args) throws Exception {

		// parse configure file
		Map configs = HangoutConfig.parse(args[0]);
		logger.debug(configs);

		ArrayBlockingQueue inputQueue = new ArrayBlockingQueue(1000, false);

		// for input in all_inputs
		ArrayList<Map> inputs = (ArrayList<Map>) configs.get("inputs");

		for (Map input : inputs) {
			Iterator<Entry<String, Map>> inputIT = input.entrySet().iterator();

			while (inputIT.hasNext()) {
				Map.Entry<String, Map> inputEntry = inputIT.next();
				String inputType = inputEntry.getKey();
				Map inputConfig = inputEntry.getValue();

				Class<?> inputClass = Class
						.forName("org.ctrip.ops.sysdev.inputs." + inputType);
				Constructor<?> ctor = inputClass.getConstructor(Map.class,
						ArrayBlockingQueue.class);
				BaseInput inputInstance = (BaseInput) ctor.newInstance(
						inputConfig, inputQueue);
				inputInstance.emit();
			}
		}

		// for filter in filters

		if (configs.containsKey("filters")) {
			ArrayList<Map> filters = (ArrayList<Map>) configs.get("filters");

			for (Map filter : filters) {
				Iterator<Entry<String, Map>> filterIT = filter.entrySet()
						.iterator();

				while (filterIT.hasNext()) {
					Map.Entry<String, Map> filterEntry = filterIT.next();
					String filterType = filterEntry.getKey();
					Map filterConfig = filterEntry.getValue();

					Class<?> filterClass = Class
							.forName("org.ctrip.ops.sysdev.filters."
									+ filterType);
					Constructor<?> ctor = filterClass.getConstructor(Map.class,
							ArrayBlockingQueue.class);
					BaseFilter filterInstance = (BaseFilter) ctor.newInstance(
							filterConfig, inputQueue);
					inputQueue = filterInstance.getOutputMQ();
					int threads = 1;
					if (filterConfig.containsKey("threads")) {
						threads = (int) filterConfig.get("threads");
					}
					for (int i = 0; i < threads; i++) {
						new Thread(filterInstance).start();
					}
				}
			}
		}

		// for output in output
		ArrayList<Map> outputs = (ArrayList<Map>) configs.get("outputs");
		ArrayList<BaseOutput> os = new ArrayList<BaseOutput>();
		for (Map output : outputs) {
			Iterator<Entry<String, Map>> outputIT = output.entrySet()
					.iterator();

			while (outputIT.hasNext()) {
				Map.Entry<String, Map> outputEntry = outputIT.next();
				String outputType = outputEntry.getKey();
				Map outputConfig = outputEntry.getValue();
				Class<?> outputClass = Class
						.forName("org.ctrip.ops.sysdev.outputs." + outputType);
				Constructor<?> ctor = outputClass.getConstructor(Map.class);

				os.add((BaseOutput) ctor.newInstance(outputConfig));
			}
		}
		try {
			while (true) {
				Map event = (Map) inputQueue.take();
				if (event != null) {
					for (BaseOutput o : os) {
						o.process(event);
					}
				}

			}
		} catch (InterruptedException e) {
		}

	}
}