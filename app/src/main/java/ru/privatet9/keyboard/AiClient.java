package ru.privatet9.keyboard;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AiClient {
    static String correct(String raw,String key) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setConnectTimeout(12000);c.setReadTimeout(25000);
        c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Authorization","Bearer "+key);
        JSONObject data=new JSONObject();
        data.put("model","gpt-4.1-mini");data.put("store",false);
        data.put("instructions","Ты корректируешь опечатки в русском и английском тексте. Сохраняй смысл, разговорный стиль, мат, названия и сокращения. Выведи только исправленный текст, без пояснений и кавычек.");
        data.put("input",raw);
        try(OutputStream os=c.getOutputStream()) {os.write(data.toString().getBytes(StandardCharsets.UTF_8));}
        int status=c.getResponseCode();
        InputStream stream=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int n;
        while((n=stream.read(chunk))!=-1){if(buffer.size()+n>300000)throw new IOException("Ответ слишком большой");buffer.write(chunk,0,n);}
        JSONObject reply=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
        if(status<200||status>=300)throw new IOException("HTTP "+status+": "+reply.optJSONObject("error").optString("message","Ошибка API"));
        JSONArray output=reply.optJSONArray("output");
        if(output==null)throw new IOException("API не вернул исправленный текст");
        StringBuilder s=new StringBuilder();
        for(int i=0;i<output.length();i++){
            JSONObject item=output.optJSONObject(i);if(item==null)continue;
            JSONArray content=item.optJSONArray("content");if(content==null)continue;
            for(int j=0;j<content.length();j++){
                JSONObject p=content.optJSONObject(j);
                if(p!=null&&"output_text".equals(p.optString("type")))s.append(p.optString("text"));
            }
        }
        String result=s.toString().trim();
        if(result.isEmpty())throw new IOException("Пустой ответ от ИИ");
        if(result.length()>Math.max(1600,raw.length()*3))throw new IOException("Слишком длинный ответ ИИ");
        return result;
    }
}
