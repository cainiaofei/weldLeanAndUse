package cdi.boot;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import chapter1.interceptorObject.HelloBean;

public class Boot {
	public static void main(String[] args){
		Weld weld = new Weld();
		WeldContainer container = weld.initialize();
		HelloBean hb = container.select(HelloBean.class).get();
		System.out.println(hb.greeting());
	}
}
