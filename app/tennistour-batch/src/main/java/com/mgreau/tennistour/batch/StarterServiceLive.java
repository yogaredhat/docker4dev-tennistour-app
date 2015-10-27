package com.mgreau.tennistour.batch;

import java.io.*;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import com.mgreau.tennistour.core.TennisMatch;
import redis.clients.jedis.BinaryJedis;

@Startup
@Singleton
public class StarterServiceLive {

    private Random random;

    private BinaryJedis jedis;

    private final String jedisKey = "matches";
    
    private static final Logger logger = Logger.getLogger("StarterService");
    
    @PostConstruct
    public void init() {
        logger.log(Level.INFO, "Initializing Cache.");

        jedis = new BinaryJedis("redis-cache");

        random = new Random();
        try {
            jedis.sadd(jedisKey.getBytes(),
                       serialize(new TennisMatch("1234",
                                                 "ROLLAND GARROS - QUARTER FINALS",
                                                 "Ferrer D.",
                                                 "es",
                                                 "Almagro N.",
                                                 "es")));
            jedis.sadd(jedisKey.getBytes(),
                       serialize(new TennisMatch("1235", "US OPEN - QUARTER FINALS", "Djokovic N.", "rs", "Berdych T.", "cz")));
            jedis.sadd(jedisKey.getBytes(),
                       serialize(new TennisMatch("1236", "US OPEN - QUARTER FINALS", "Murray A.", "gb", "Chardy J.", "fr")));
            jedis.sadd(jedisKey.getBytes(),
                       serialize(new TennisMatch("1237", "US OPEN - QUARTER FINALS", "Federer R.", "ch", "Tsonga J.W.", "fr")));
        }catch (Exception ex){
            logger.severe("Error with Redis" + ex.getCause());
        }
    }

    @PreDestroy
    public void destroy(){
        jedis.quit();
    }
    
    @Schedule(second="*/2", minute="*",hour="*", persistent=false)
    public void play() {

      Set<byte[]> matches = jedis.smembers(jedisKey.getBytes());

      for (Iterator<byte[]> it = matches.iterator(); it.hasNext();) {
        TennisMatch m = null;
        try {

           m = (TennisMatch) deserialize(it.next());
          jedis.srem(jedisKey.getBytes(), serialize(m));


          if (m.isFinished()){
            //add a timer to restart a match after 20 secondes
            m.reset();
          }
          //Handle point
          if (random.nextInt(2) == 1){
            m.playerOneScores();
          } else {
            m.playerTwoScores();
          }

          try {
            logger.info("One point...Writing into redis cache.");
            jedis.sadd(jedisKey.getBytes(), serialize(m));
          } catch (Exception e){
            logger.severe("Error when writing to the cache." + e.getCause());
          }
          Long count = jedis.objectRefcount(("matches").getBytes());
          logger.info("Number of matches: " + count);
        } catch (Exception e) {
          e.printStackTrace();
        }
    	}
    }

  public TennisMatch getMatchFromCache(final Long index) throws Exception{
    return (TennisMatch) deserialize(jedis.lindex(jedisKey.getBytes(), index));
  }
    
    public BinaryJedis getCache(){
    	return jedis;
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
