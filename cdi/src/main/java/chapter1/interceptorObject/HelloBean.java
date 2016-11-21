package chapter1.interceptorObject;

import javax.interceptor.Interceptors;

import chapter1.Interceptor.InterceptorTest;

public class HelloBean {
	@Interceptors(InterceptorTest.class)
	public String greeting(){
		return "world!";
	}
}
