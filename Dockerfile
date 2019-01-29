FROM anapsix/alpine-java:8_server-jre

RUN echo export LANG=en_US.UTF-8 >> /etc/profile
RUN echo export LANGUAGE=en_US.UTF-8 >> /etc/profile
RUN echo export LC_ALL=en_US.UTF-8 >> /etc/profile

#RUN yum -y install which
#RUN yum -y install curl
#RUN yum -y install vim

# Setup
COPY target/cochat /root/cochat
WORKDIR /root/cochat

ENV PAGER=more
# A bit help in shell
ENV PS1='$PWD$ '

EXPOSE 54088

ENTRYPOINT ["bash", "bin/start.sh"]
