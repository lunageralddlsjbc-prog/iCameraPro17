package com.lunageralddlsjbc.icamerapro17.processing

enum class PhotographicStyle(val styleName: String, val matrix: FloatArray) {
    STANDARD("Standard", floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)),
    VIBRANT("Vibrant", floatArrayOf(1.3f,-0.1f,-0.1f,0f,10f, -0.1f,1.3f,-0.1f,0f,10f, -0.1f,-0.1f,1.3f,0f,10f, 0f,0f,0f,1f,0f)),
    WARM("Warm", floatArrayOf(1.1f,0f,0f,0f,15f, 0f,1.05f,0f,0f,5f, 0f,0f,0.95f,0f,-10f, 0f,0f,0f,1f,0f)),
    COOL("Cool", floatArrayOf(0.95f,0f,0f,0f,-10f, 0f,1f,0f,0f,5f, 0f,0f,1.1f,0f,15f, 0f,0f,0f,1f,0f))
}
