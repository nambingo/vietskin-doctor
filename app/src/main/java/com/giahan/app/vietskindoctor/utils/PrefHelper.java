package com.giahan.app.vietskindoctor.utils;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultString;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

/**
 * Created by pham.duc.nam on 17/05/2018.
 */
@SharedPref(value = SharedPref.Scope.UNIQUE)
public interface PrefHelper {
    @DefaultString("")
    String email();

    @DefaultString("")
    String token();

    @DefaultString("")
    String user();

    String currentDoctor();

    String filterDoctor();

    String dataSelected();

    @DefaultBoolean(true)
    boolean isStartApp();

    @DefaultBoolean(true)
    boolean isLoginFb();

    String dataSearch();

    @DefaultBoolean(false)
    boolean isLogged();

    @DefaultBoolean(true)
    boolean isUpdateFirst();

    String sessionBodyNotDoctor();

    String resultData();

    String questionList();

    String question1();

    @DefaultBoolean(false)
    boolean isListDoctor();
}
