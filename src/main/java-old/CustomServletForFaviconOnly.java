package org.terracotta.tinypounder;

import com.vaadin.server.BootstrapFragmentResponse;
import com.vaadin.server.BootstrapListener;
import com.vaadin.server.BootstrapPageResponse;
import com.vaadin.server.SessionInitListener;
import com.vaadin.spring.server.SpringVaadinServlet;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;

// from http://www.codemarvels.com/2017/04/how-to-show-custom-favicon-in-vaadin-ui/
// gif from https://www.reddit.com/r/noisygifs/comments/1w5d0q/this_hammer_gif/ or
// https://giphy.com/gifs/hammer-noisygifs-RAUh1XkOJnF4c/
@Component("vaadinServlet")
public class CustomServletForFaviconOnly extends SpringVaadinServlet {

  @Override
  protected void servletInitialized() throws ServletException {
    super.servletInitialized();
    getService().addSessionInitListener((SessionInitListener) event -> event.getSession()
        .addBootstrapListener(new BootstrapListener() {
                                @Override
                                public void modifyBootstrapFragment(
                                    BootstrapFragmentResponse response) {
                                }

                                @Override
                                public void modifyBootstrapPage(BootstrapPageResponse response) {
                                  response.getDocument().head().
                                      getElementsByAttributeValue("rel", "shortcut icon")
                                      .attr("href", "/favicon2.gif")
                                      .attr("type", "image/gif")
                                  ;
                                  response.getDocument().head()
                                      .getElementsByAttributeValue("rel", "icon")
                                      .attr("href", "./favicon2.gif")
                                      .attr("type", "image/gif");
                                }
                              }
        ));

  }
}