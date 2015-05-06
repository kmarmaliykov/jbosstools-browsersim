package org.jboss.tools.browsersim.browser;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.widgets.Composite;

public class BrowserWithFunctions extends Browser {

	public BrowserWithFunctions(Composite parent, int style) {
		super(parent, style);
	}
	
	public IDisposable registerBrowserFunction(String name, final IBrowserFunction iBrowserFunction) {
		final BrowserFunction function = new BrowserFunction(this, name) {
			@Override
			public Object function(Object[] arguments) {
				return iBrowserFunction.function(arguments);
			}
		}; 
		
		return new IDisposable() {
			@Override
			public boolean isDisposed() {
				return function.isDisposed();
			}
			
			@Override
			public void dispose() {
				function.dispose();
			}
		};
	}
}
