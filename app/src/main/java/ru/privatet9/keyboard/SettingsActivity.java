package ru.privatet9.keyboard;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputType;
import android.graphics.Typeface;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.view.View;
import android.content.Context;

public final class SettingsActivity extends Activity {
    private LinearLayout screen;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(8),0,dp(8));return t;
    }
    private Button button(String s,Runnable f) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        screen.addView(b);b.setOnClickListener(v->f.run());return b;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this);screen=new LinearLayout(this);
        screen.setPadding(dp(20),dp(22),dp(20),dp(32));
        screen.setOrientation(LinearLayout.VERTICAL);
        sc.addView(screen);setContentView(sc);
        screen.addView(text("Умный Т9",27,true));
        screen.addView(text("Клавиатура для Samsung с русской и английской раскладкой, офлайн-подсказками и кнопкой ✨ для смысловой коррекции.",15,false));
        screen.addView(text("1. Включи клавиатуру в настройках Android.\n2. Выбери «Умный Т9» вместо Samsung Keyboard.\n3. Начни печатать в любом приложении.",16,false));
        button("1. Открыть настройки клавиатур",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("2. Выбрать клавиатуру",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        CheckBox auto=new CheckBox(this);
        auto.setText("Автоматически исправлять только известные опечатки");
        auto.setChecked(getSharedPreferences("keyboard_settings",0).getBoolean("auto_correct",false));
        auto.setOnCheckedChangeListener((a,v)->getSharedPreferences("keyboard_settings",0).edit().putBoolean("auto_correct",v).apply());
        screen.addView(auto);
        screen.addView(text("Для ✨ требуется отдельный OpenAI API-ключ с оплатой API. ChatGPT Plus этого ключа не заменяет. Текст отправляется только по нажатию ✨ и только на api.openai.com.",14,false));
        EditText key=new EditText(this);
        key.setSingleLine(true);key.setHint("sk-... (необязательно)");
        key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(SecretStore.read(this));screen.addView(key);
        button("Сохранить API-ключ",()->{
            try{SecretStore.save(this,key.getText().toString().trim()); Toast.makeText(this,"Ключ сохранён в Android Keystore",Toast.LENGTH_LONG).show();}
            catch(Exception e){Toast.makeText(this,"Ошибка сохранения: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        screen.addView(text("Проверка клавиатуры:",16,true));
        EditText example=new EditText(this);
        example.setHint("Попробуй набрать «нп работает»");
        example.setMinLines(2);example.setGravity(48);screen.addView(example);
        screen.addView(text("Установка этого APK не удаляет Samsung Keyboard. Вернуть прежнюю клавиатуру можно в любое время через настройки системы.",13,false));
    }
}
