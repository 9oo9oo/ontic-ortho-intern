package com.example.myapplication;

import android.content.Context;
import android.graphics.ColorSpace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class OBJLoader {
    public static Model loadOBJ(Context context, int resourceId) {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        List<Float> vertices = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> textures = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        try {
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split("\\s+");
                switch (tokens[0]) {
                    case "v":
                        vertices.add(Float.parseFloat(tokens[1]));
                        vertices.add(Float.parseFloat(tokens[2]));
                        vertices.add(Float.parseFloat(tokens[3]));
                        break;
                    case "vn":
                        normals.add(Float.parseFloat(tokens[1]));
                        normals.add(Float.parseFloat(tokens[2]));
                        normals.add(Float.parseFloat(tokens[3]));
                        break;
                    case "vt":
                        textures.add(Float.parseFloat(tokens[1]));
                        textures.add(Float.parseFloat(tokens[2]));
                        break;
                    case "f":
                        for (int i = 1; i < tokens.length; i++) {
                            String[] parts = tokens[i].split("/");
                            indices.add(Integer.parseInt(parts[0]) - 1);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Model(vertices, normals, textures, indices);
    }
}