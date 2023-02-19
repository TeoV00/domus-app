package it.unibo.isi.seiot.smartgarden.utils;

public enum GarageState {
    CLOSE("Chiuso"),
    OPEN("Aperto"),
    CLOSING("In chiusura..."),
    OPENING("In apertura..."),
    CLOSING_P("Pausa"), //closing in pause
    OPENING_P("Pausa"), //opening in pause
    REQ_CLOSE("Richiesta chiusura"), //close request
    REQ_OPEN("Richiesta apertura"), //open request
    REQ_PAUSE("Richiesta pausa");

    private final String descr;

    GarageState(final String descr) {
        this.descr = descr;
    }

    public int getOrdinal() {
        return this.ordinal();
    }

    public String getDescr() {
        return  this.descr;
    }

}