package zsl.web.untils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JWTtools {

    public static String createJWT(String id, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("usrename", name);
        String jwt = Jwts.builder().signWith(SignatureAlgorithm.HS256, "zsl").//设置签名算法和密钥
                addClaims(claims).//添加自定义属性
                setExpiration(new Date(System.currentTimeMillis() + 3600*1000)).//设置过期时间
                compact();//创建jwt
        return jwt;
    }

    //验证JWT
    public static boolean checkJWT(String jwt) {
        try {
            Claims Q = Jwts.parser().setSigningKey("zsl").parseClaimsJws(jwt).getBody();
            return true;
        } catch (Exception e) {
            System.out.println("令牌错误，重新登录");
            return false;
        }
    }


}
