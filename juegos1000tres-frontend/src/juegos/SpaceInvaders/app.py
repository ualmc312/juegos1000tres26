from flask import Flask, render_template, request, jsonify

app = Flask(__name__)

# Dictionary to store scores by player name. 
# Key: player_name (str), Value: maximum_score (int)
scoreboard = {}

@app.route('/')
def home():
    return render_template('space_invaders.html')

@app.route('/api/score', methods=['GET'])
def get_score():
    # Return a list of scores sorted backwards (highest first)
    sorted_scores = sorted([{"player": k, "score": v} for k, v in scoreboard.items()], 
                           key=lambda x: x['score'], reverse=True)
    return jsonify({"scores": sorted_scores})

@app.route('/api/score', methods=['POST'])
def save_score():
    data = request.get_json()
    if data and 'score' in data and 'player' in data:
        player_name = data['player'].strip()
        score = data['score']
        
        # Guardamos la puntuación solo si es mayor que la anterior o si es nuevo
        if player_name:
            current_best = scoreboard.get(player_name, 0)
            if score > current_best:
                scoreboard[player_name] = score
                
            return jsonify({"status": "success", "player": player_name, "score": scoreboard[player_name]})
            
    return jsonify({"status": "error", "message": "Invalid data, Requires 'player' and 'score'"}), 400

if __name__ == '__main__':
    # Listen on all standard network interfaces so other LAN users can connect
    app.run(debug=True, host='0.0.0.0', port=5000)
