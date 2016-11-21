package chapter1.Interceptor;

import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

public class InterceptorTest {
	@AroundInvoke
	public Object invoke(InvocationContext context) throws Exception{
		return "xscd,"+context.proceed();
	}
}
