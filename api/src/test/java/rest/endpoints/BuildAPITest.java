/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins.paginatedbuilds.rest.endpoints;

import io.jenkins.plugins.paginatedbuilds.rest.external.BuildExt;
import io.jenkins.plugins.paginatedbuilds.rest.external.BuildResponse;
import io.jenkins.plugins.paginatedbuilds.rest.endpoints.*;
import io.jenkins.plugins.paginatedbuilds.util.JSONReadWrite;
import com.gargoylesoftware.htmlunit.Page;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import org.xml.sax.SAXException;
import java.io.IOException;
import java.util.List;


/**
 * Test the raw build APIs
 */
public class BuildAPITest {
    private static long startTime;

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @After
    public void after() {
    }

    @BeforeClass
    public static void setup() {
        startTime = System.currentTimeMillis();
    }

    @Test
    public void testFreestyleOneBuild() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");

        QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
        jenkinsRule.assertBuildStatusSuccess(build);

        BuildResponse builds = getBuilds(job, "builds");

        Assert.assertEquals(1, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(0);
        assertBuildInfoOkay(job, buildExt, "1");
    }

    @Test
    public void testFreestyleTwoBuilds() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");

        QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
        jenkinsRule.assertBuildStatusSuccess(build);
        QueueTaskFuture<FreeStyleBuild> build2 = job.scheduleBuild2(0);
        jenkinsRule.assertBuildStatusSuccess(build2);

        BuildResponse builds = getBuilds(job, "builds");

        Assert.assertEquals(2, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(1);
        assertBuildInfoOkay(job, buildExt, "2");
    }

    @Test
    public void testFreestyleTenBuildsSizeFive() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");

        for (int i = 0; i < 10; i++) {
            QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
            jenkinsRule.assertBuildStatusSuccess(build);
        }

        BuildResponse builds = getBuilds(job, "builds?size=5");

        Assert.assertEquals(5, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(4);
        assertBuildInfoOkay(job, buildExt, "5");
    }

    @Test
    public void testFreestyleTenBuildsStartSix() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");

        for (int i = 0; i < 10; i++) {
            QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
            jenkinsRule.assertBuildStatusSuccess(build);
        }

        BuildResponse builds = getBuilds(job, "builds?start=6");

        Assert.assertEquals(5, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(4);
        assertBuildInfoOkay(job, buildExt, "10");
    }

    @Test
    public void testFreestyleTenBuildsStartSixSizeTwo() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");

        for (int i = 0; i < 10; i++) {
            QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
            jenkinsRule.assertBuildStatusSuccess(build);
        }

        BuildResponse builds = getBuilds(job, "builds?start=6&size=2");

        Assert.assertEquals(2, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(1);
        assertBuildInfoOkay(job, buildExt, "7");
    }

    @Test
    public void testPipelineBuild() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "TestJob");

        job.setDefinition(new CpsFlowDefinition("" +
                "node {" +
                "   stage ('Build'); " +
                "   echo ('Building'); " +
                "   stage ('Test'); " +
                "   echo ('Testing'); " +
                "   stage ('Deploy'); " +
                "     writeFile file: 'file.txt', text:'content'; " +
                "     archive(includes: 'file.txt'); " +
                "   echo ('Deploying'); " +
                "}", true));

        QueueTaskFuture<WorkflowRun> build = job.scheduleBuild2(0);
        jenkinsRule.assertBuildStatusSuccess(build);

        BuildResponse builds = getBuilds(job, "builds");

        Assert.assertEquals(1, builds.getCount());
        BuildExt buildExt = builds.getBuilds().get(0);
        assertBuildInfoOkay(job, buildExt, "1");
    }

    @Test
    public void testAdditionalBuilds() throws Exception {
        FreeStyleProject job = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "TestJob");
        BuildAPI buildAPI = new BuildAPI();
        for (int i = 0; i < 10; i++) {
            QueueTaskFuture<FreeStyleBuild> build = job.scheduleBuild2(0);
            jenkinsRule.assertBuildStatusSuccess(build);
        }

        RangeSet range = RangeSet.fromString("1-8", false);
        List<Run> rawBuilds = ((Job) job).getBuilds(range);
        List<Run> additionalBuilds = buildAPI.getAdditionalBuilds((Job) job, rawBuilds, 15);

        Assert.assertEquals(10, additionalBuilds.size());
    }

    private void assertBuildInfoOkay(Job job, BuildExt buildExt, String jobNumber) {
        Assert.assertEquals("TestJob #" + jobNumber, buildExt.getFullName());
        Assert.assertEquals("SUCCESS", buildExt.getResult());
        Assert.assertTrue(buildExt.getDuration() > 0);
        Assert.assertTrue(buildExt.getStartTimeMillis() > startTime);
        Assert.assertEquals(jobNumber, buildExt.getId());
        Assert.assertTrue(buildExt.getStartTimeMillis() > buildExt.getQueueTimeMillis());
        Assert.assertEquals("", buildExt.getBuiltOn());
    }

    private BuildResponse getBuilds(Job job, String endpoint) throws IOException, SAXException{
      JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();

      String buildsUrl = job.getUrl() + endpoint;
      Page buildsPage = webClient.goTo(buildsUrl, "application/json");
      String jsonResponse = buildsPage.getWebResponse().getContentAsString();

      JSONReadWrite jsonReadWrite = new JSONReadWrite();

      return jsonReadWrite.fromString(jsonResponse, BuildResponse.class);
    }
}
