Bitrete Jackrabbit RMI publishing servlet for Apache Tomcat
===========================================================

Problem
-------
When you deploy Jackrabbit repository into Tomcat as [shared resource (deployment Model 2)](http://jackrabbit.apache.org/deployment-models.html#DeploymentModels-Model2%3ASharedJ2EEResource), and then you try to enable RMI access to shared repository using RepositoryStartup servlet you get failed. The reason is that RepositoryStartup servlet can only publish locally created repository but not shared one. [See this for details](http://jackrabbit.510166.n4.nabble.com/Providing-RMI-access-for-a-model-2-shared-deployment-td512615.html).


Solution
--------
To solve this problem you need to write simple servlet which gets repository reference via JNDI and publish it via RMI. So we did it for you :)

How to build
------------
Just run provided ant script by following command line:
```ant /release distr```
It produces ready-to-deploy war file in folder distr/war.

Configuration
-------------
You can configure RMI and repository ports in servlet's web.xml. All settings are well documented there. 

Feel free to ask questions about this product info [at] bitrete.ru. ([www.bitrete.ru](http://www.bitrete.ru))
