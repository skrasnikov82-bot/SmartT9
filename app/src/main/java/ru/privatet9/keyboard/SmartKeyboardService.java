package ru.privatet9.keyboard;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.Gravity;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.text.InputType;
import android.widget.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.*;

public final class SmartKeyboardService extends InputMethodService {
    private static final int BG=0xffdfe3eb,INK=0xff202a3e,SPECIAL=0xffb6c1d2,BLUE=0xff3b63bc;
    private static final Pattern WORD=Pattern.compile("[\\p{L}]+$");
    private static final String RU="я ты мы вы они он она это как что где когда почему потому уже ещё надо можно нужно будет хочу есть нет да не на по из для мне вам тебя меня просто работа работает делать сделал сделать привет спасибо пожалуйста хорошо плохо день завтра сегодня вчера утром вечером интернет телефон самсунг клавиатура слово исправить правильно текст сообщение ошибка опечатка купить найти цена товар поставка сервер программа приложение работает работает";
    private static final String EN="the this that have hello world thanks thank you for where what when how why good bad keyboard message correction android phone text app internet can not yes no please work working";
    private static final Map<String,String> FIXES=new HashMap<>();
    static {String[][] pairs={{"нп","не"},{"нк","не"},{"превет","привет"},{"спосибо","спасибо"},{"пасиба","спасибо"},{"севодня","сегодня"},{"щас","щас"},{"тольятии","тольятти"},{"работтает","работает"},{"вчеа","вчера"},{"еслт","если"},{"пожалуста","пожалуйста"},{"неь","нет"},{"teh","the"},{"thnaks","thanks"}};for(String[] p:pairs)FIXES.put(p[0],p[1]);}
    private LinearLayout root,rows,bar;
    private final TextView[] suggestions=new TextView[3];
    private TextView ai;
    private boolean en=false,upper=false,symbols=false,sensitive=false,numeric=false,skipSuggestions=false,busy=false;
    private int version=0,session=0;
    private EditorInfo editor;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onStartInput(EditorInfo a,boolean restarting){
        super.onStartInput(a,restarting);editor=a;session++;version++;
        int t=a==null?InputType.TYPE_CLASS_TEXT:a.inputType;
        int kind=t&InputType.TYPE_MASK_CLASS,variation=t&InputType.TYPE_MASK_VARIATION;
        numeric=kind==InputType.TYPE_CLASS_NUMBER||kind==InputType.TYPE_CLASS_PHONE||kind==InputType.TYPE_CLASS_DATETIME;
        sensitive=(kind==InputType.TYPE_CLASS_TEXT&&(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))||(kind==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        skipSuggestions=sensitive||(t&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0||(a!=null&&(a.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0);
        symbols=false;upper=false;
    }
    @Override public void onStartInputView(EditorInfo a,boolean restarting){super.onStartInputView(a,restarting);if(root!=null){draw();update();}}
    @Override public void onFinishInput(){session++;version++;super.onFinishInput();}
    @Override public void onUpdateSelection(int os,int oe,int ns,int ne,int cs,int ce){
        super.onUpdateSelection(os,oe,ns,ne,cs,ce);if(os!=ns||oe!=ne)version++;if(root!=null)update();
    }
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(7));return d;}
    private TextView view(String label,int color,int font){
        TextView t=new TextView(this);t.setText(label);t.setTextSize(font);t.setTextColor(INK);
        t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);t.setBackground(bg(color));return t;
    }
    @Override public View onCreateInputView(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
        root.setPadding(dp(3),dp(4),dp(3),dp(6));
        bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(43)));
        TextView globe=view("🌐",SPECIAL,18);bar.addView(globe,new LinearLayout.LayoutParams(dp(38),dp(36)));
        globe.setOnClickListener(v->language());
        for(int i=0;i<3;i++){
            final int index=i;TextView t=view("",0xfff9fafd,13);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);p.setMargins(dp(2),0,dp(2),0);
            bar.addView(t,p);suggestions[i]=t;t.setOnClickListener(v->apply(index));
        }
        ai=view("✨",BLUE,19);ai.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(dp(40),dp(36));bar.addView(ai,a);
        ai.setContentDescription("Исправить выбранный текст при помощи ИИ");
        ai.setOnClickListener(v->correct());
        TextView gear=view("⚙",SPECIAL,18);
        bar.addView(gear,new LinearLayout.LayoutParams(dp(38),dp(36)));
        gear.setOnClickListener(v->{Intent it=new Intent(this,SettingsActivity.class);it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it);});
        rows=new LinearLayout(this);rows.setOrientation(1);root.addView(rows);
        draw();update();return root;
    }
    private String[] split(String s){String[] a=new String[s.length()];for(int i=0;i<s.length();i++)a[i]=s.substring(i,i+1);return a;}
    private void draw(){
        if(rows==null)return;rows.removeAllViews();
        if(numeric||symbols){row(split("1234567890"),false);row(new String[]{"@","#","₽","%","&","-","+","(",")","/"},false);row(new String[]{"!","?",":",";","/","*","'","⌫"},false);}
        else if(en){row(split("qwertyuiop"),false);row(split("asdfghjkl"),false);row(split("zxcvbnm"),true);}
        else {row(split("йцукенгшщзхъ"),false);row(split("фывапролджэ"),false);row(split("ячсмитьбю"),true);}
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);
        rows.addView(bottom,new LinearLayout.LayoutParams(-1,dp(55)));
        add(bottom,symbols?"АБВ":"?123",1.15f);add(bottom,en?"EN/РУ":"РУ/EN",1.1f);
        add(bottom,",",.8f);add(bottom,"пробел",4.2f);add(bottom,".",.8f);add(bottom,"↵",1.2f);
        if(ai!=null){ai.setText(busy?"…":"✨");ai.setAlpha(sensitive||numeric||skipSuggestions?.3f:1f);}
    }
    private void row(String[] a,boolean shift){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);
        rows.addView(line,new LinearLayout.LayoutParams(-1,dp(50)));
        if(shift)add(line,"⇧",1.25f);
        for(String s:a)add(line,s,1);
        if(shift)add(line,"⌫",1.25f);
    }
    private void add(LinearLayout line,String s,float weight){
        boolean special=s.equals("⌫")||s.equals("⇧")||s.equals("РУ/EN")||s.equals("EN/РУ")||s.equals("?123")||s.equals("АБВ")||s.equals("↵");
        TextView t=view(s.equals("пробел")?"пробел":(upper&&!symbols?s.toUpperCase(Locale.ROOT):s),s.equals("↵")?BLUE:(special?SPECIAL:Color.WHITE),s.equals("пробел")?12:(s.length()>2?11:19));
        if(s.equals("↵"))t.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),weight);p.setMargins(dp(2),dp(2),dp(2),dp(2));line.addView(t,p);
        t.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);press(s);});
        if(s.equals("⌫"))t.setOnLongClickListener(v->{deleteWord();return true;});
        if(s.equals("е")&&!en&&!symbols)t.setOnLongClickListener(v->{press(upper?"Ё":"ё");return true;});
    }
    private void language(){en=!en;upper=false;draw();update();}
    private void press(String s){
        if(s.equals("РУ/EN")||s.equals("EN/РУ")){language();return;}
        if(s.equals("?123")||s.equals("АБВ")){symbols=!symbols;draw();update();return;}
        if(s.equals("⇧")){upper=!upper;draw();return;}
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;version++;
        if(s.equals("⌫")){CharSequence sel=ic.getSelectedText(0);
            if(sel!=null&&sel.length()>0)ic.commitText("",1);else ic.deleteSurroundingText(1,0);}
        else if(s.equals("пробел")){if(!sensitive&&!skipSuggestions&&!symbols&&!numeric&&getSharedPreferences("keyboard_settings",0).getBoolean("auto_correct",false))autofix(ic);ic.commitText(" ",1);}
        else if(s.equals("↵")){
            int action=editor==null?EditorInfo.IME_ACTION_NONE:editor.imeOptions&EditorInfo.IME_MASK_ACTION;
            if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED)ic.performEditorAction(action);
            else ic.commitText("\n",1);
        }else{ic.commitText(upper&&!symbols?s.toUpperCase(Locale.ROOT):s,1);if(upper){upper=false;draw();}}
        update();
    }
    private void deleteWord(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;CharSequence x=ic.getTextBeforeCursor(120,0);if(x==null)return;
        int n=x.length(),i=n;while(i>0&&Character.isWhitespace(x.charAt(i-1)))i--;
        while(i>0&&Character.isLetterOrDigit(x.charAt(i-1)))i--;
        ic.deleteSurroundingText(n-i,0);version++;update();
    }
    private String last(){
        InputConnection ic=getCurrentInputConnection();
        CharSequence b=ic==null?null:ic.getTextBeforeCursor(70,0);
        Matcher m=WORD.matcher(b==null?"":b);return m.find()?m.group():"";
    }
    private String fix(String s){
        String v=FIXES.get(s.toLowerCase(Locale.ROOT));
        if(v==null)return s;
        return !s.isEmpty()&&Character.isUpperCase(s.charAt(0))?
           v.substring(0,1).toUpperCase(Locale.ROOT)+v.substring(1):v;
    }
    private void autofix(InputConnection ic){String old=last(),next=fix(old);if(old.isEmpty()||old.equals(next))return;
        ic.beginBatchEdit();ic.deleteSurroundingText(old.length(),0);ic.commitText(next,1);ic.endBatchEdit();}
    private void update(){
        if(suggestions[0]==null)return;
        for(TextView t:suggestions)t.setText("");
        if(sensitive||numeric||skipSuggestions||symbols)return;
        String w=last();if(w.isEmpty())return;
        LinkedHashSet<String> picks=new LinkedHashSet<>();String preferred=fix(w);
        if(!preferred.equals(w))picks.add(preferred);
        if(w.length()>1){
            String dict=en?EN:RU;String lw=w.toLowerCase(Locale.ROOT);
            for(String x:dict.split(" ")){if(x.startsWith(lw)&&!x.equals(lw))picks.add(x);if(picks.size()>=3)break;}
        }
        picks.add(w);int i=0;for(String s:picks){if(i>=3)break;suggestions[i++].setText(s);}
    }
    private void apply(int index){
        if(sensitive||numeric||skipSuggestions)return;
        String old=last(),next=suggestions[index].getText().toString();
        if(old.isEmpty()||next.isEmpty()||old.equals(next))return;
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        ic.beginBatchEdit();ic.deleteSurroundingText(old.length(),0);ic.commitText(next,1);ic.endBatchEdit();
        version++;update();
    }
    private void correct(){
        if(busy){toast("ИИ уже обрабатывает текст");return;}
        if(sensitive||numeric||skipSuggestions){toast("В этом поле ИИ отключён");return;}
        final InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        final String key=SecretStore.read(this);
        if(key.isEmpty()){toast("Добавь API-ключ в настройках ⚙");return;}
        CharSequence sel=ic.getSelectedText(0);
        final boolean selected=sel!=null&&sel.length()>0;
        final String old;
        if(selected)old=sel.toString();else{
            CharSequence before=ic.getTextBeforeCursor(400,0);if(before==null)return;
            String text=before.toString();int pos=Math.max(text.lastIndexOf('\n'),text.lastIndexOf('\r'));
            old=text.substring(pos+1);
        }
        if(old.trim().isEmpty()||old.length()>400){toast("Выдели текст не длиннее 400 символов");return;}
        final int v=version,ss=session;busy=true;if(ai!=null)ai.setText("…");
        worker.execute(()->{
            String result=null,error=null;try{result=AiClient.correct(old,key);}
            catch(Exception e){error=e.getMessage();}
            final String ans=result,fail=error;
            main.post(()->{
                busy=false;if(ai!=null)ai.setText("✨");
                if(fail!=null){toast("ИИ: "+fail);return;}
                if(ans==null||ans.equals(old)){toast("Текст уже выглядит правильно");return;}
                if(version!=v||session!=ss||ic!=getCurrentInputConnection()){toast("Текст изменился, ответ не подставлен");return;}
                if(selected){
                    CharSequence now=ic.getSelectedText(0);
                    if(now==null||!old.contentEquals(now)){toast("Выделение изменилось");return;}
                    ic.commitText(ans,1);
                }else{
                    CharSequence now=ic.getTextBeforeCursor(400,0);
                    if(now==null||!now.toString().endsWith(old)){toast("Курсор или текст изменился");return;}
                    ic.beginBatchEdit();ic.deleteSurroundingText(old.length(),0);ic.commitText(ans,1);ic.endBatchEdit();
                }
                version++;update();
            });
        });
    }
}
