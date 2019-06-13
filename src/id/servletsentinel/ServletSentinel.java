package id.servletsentinel;

import static java.lang.System.out;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class ServletSentinel implements Filter {

    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log("Started");
        servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, final ServletResponse response, FilterChain chain)
            throws IOException, ServletException 
    {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintWriter pw = new PrintWriter(baos);
        final ServletOutputStream sos = new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                baos.write(b);
            }

            @Override
            public boolean isReady() {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public void setWriteListener(WriteListener arg0) {
                // TODO Auto-generated method stub
                
            }
        };
        ServletResponse wrappedResponse = new HttpServletResponseWrapper((HttpServletResponse) response) {
            @Override
            public ServletOutputStream getOutputStream() throws IOException {
                log("getOutputStream");
                return sos;
            }
            @Override
            public PrintWriter getWriter() throws IOException {
                log("getWriter");
                return pw;
            }
        };
        chain.doFilter(request, wrappedResponse);
        pw.flush();
        transformResponse(servletContext, 
            headers(httpRequest), 
            params(httpRequest.getParameterMap()), 
            new StringReader(baos.toString()),
            response.getWriter());
    }

    @Override
    public void destroy() {
    }
    
    private void transformResponse(ServletContext context, String headers, String params, 
            Reader in, Writer out) throws IOException
    {
        String script = "/tmp/servlet-sentinel.sh";
        if (script == null || !new File(script).exists()) {
            log("Script " + script + " not found");
            redirect(in, out);
            return;
        }
        Process proc = Runtime.getRuntime().exec(new String[] {"/bin/bash", 
            script, headers, params});
        redirect(in, new OutputStreamWriter(proc.getOutputStream()));
        proc.getOutputStream().close();
        redirect(new InputStreamReader(proc.getInputStream()), out);
    }
    
    private static String headers(HttpServletRequest request) {
        StringBuilder buf = new StringBuilder();
        Enumeration<?> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            buf.append(name + "=" + request.getHeader(name)).append('\n');
        }
        return buf.toString();
    }
    
    private static String params(Map<String, ?> parameterMap) {
        StringBuilder buf = new StringBuilder();
        for (Object k: parameterMap.keySet()) {
            String name = (String) k;
            String val = "";
            if (parameterMap.get(name) != null)
                val = Arrays.asList((String[])parameterMap.get(name)).toString();
            buf.append(name).append("=").append(val).append('\n');
        }
        return buf.toString();
    }
    
    private static void redirect(Reader reader, Writer writer) {
        try {
            char[] buf = new char[1024];
            int len = 0;
            while ((len = reader.read(buf)) != -1)
                writer.write(buf, 0, len);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void log(String msg) {
        out.format("ServletSentinel: %s", msg);
    }
}
