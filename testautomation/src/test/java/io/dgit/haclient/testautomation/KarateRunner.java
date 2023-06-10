package io.dgit.haclient.testautomation;

import com.intuit.karate.junit5.Karate;

// see https://github.com/karatelabs/karate#junit-5
public class KarateRunner {

    @Karate.Test
    Karate testSample() {
        return Karate.run("haclient").relativeTo(getClass());
    }

}
