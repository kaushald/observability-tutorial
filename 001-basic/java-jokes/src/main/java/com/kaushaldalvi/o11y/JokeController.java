package com.kaushaldalvi.o11y;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
public class JokeController {

    private static final String[] JOKES = new String[]{
            "My wife told me to stop impersonating a flamingo. I had to put my foot down.",
            "I went to buy some camo pants but couldn’t find any.",
            "I failed math so many times at school, I can’t even count.",
            "I used to have a handle on life, but then it broke.",
            "I was wondering why the frisbee kept getting bigger and bigger, but then it hit me.",
            "I heard there were a bunch of break-ins over at the car park. That is wrong on so many levels.",
            "I want to die peacefully in my sleep, like my grandfather… Not screaming and yelling like the passengers in his car.",
            "When life gives you melons, you might be dyslexic.",
            "Don’t you hate it when someone answers their own questions? I do.",
            "I told him to be himself; that was pretty mean, I guess.",
            "I know they say that money talks, but all mine says is ‘Goodbye.’",
            "My father has schizophrenia, but he’s good people.",
            "The problem with kleptomaniacs is that they always take things literally.",
            "I can’t believe I got fired from the calendar factory. All I did was take a day off.",
            "Most people are shocked when they find out how bad I am as an electrician.",
            "Never trust atoms; they make up everything.",
            "I used to think I was indecisive. But now I’m not so sure.",
            "Russian dolls are so full of themselves.",
            "The easiest time to add insult to injury is when you’re signing someone’s cast.",
            "Light travels faster than sound, which is the reason that some people appear bright before you hear them speak.",
            "A termite walks into the bar and asks, ‘Is the bar tender here?’",
            "People who use selfie sticks really need to have a good, long look at themselves.",
            "Two fish are in a tank. One says, ‘How do you drive this thing?’",
            "Just burned 2,000 calories. That’s the last time I leave brownies in the oven while I nap."
    };
    private static final Random generator = new Random();

    @RequestMapping("/joke")
    public String index() {
        try {
            Thread.sleep(generator.nextInt(250));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return getJoke();
    }

    public String getJoke() {
        int rnd = generator.nextInt(JOKES.length);
        try {
            Thread.sleep(generator.nextInt(250));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return JOKES[rnd];
    }


}
